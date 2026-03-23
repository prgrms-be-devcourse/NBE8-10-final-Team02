package com.back.backend.domain.github.service;

import com.back.backend.domain.github.dto.request.GithubConnectRequest;
import com.back.backend.domain.github.dto.response.GithubConnectionResponse;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.GithubSyncStatus;
import com.back.backend.domain.github.repository.GithubConnectionRepository;
import com.back.backend.domain.github.repository.GithubRepositoryRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * GitHub 연결(github_connections) 생성/갱신을 담당한다.
 *
 * 핵심 흐름:
 *   1. GitHub API 호출로 사용자 정보와 repo 목록을 가져온다 (트랜잭션 밖).
 *   2. 결과를 DB에 저장한다 (트랜잭션 안).
 *
 * GitHub 로그인 사용자: 로그인 시 받은 OAuth token을 accessToken 필드로 전달.
 * Google/Kakao 로그인 사용자: 별도 GitHub OAuth 완료 후 token 전달.
 */
@Service
public class GithubConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GithubConnectionService.class);

    private final GithubApiClient githubApiClient;
    private final GithubConnectionRepository connectionRepository;
    private final GithubRepositoryRepository repositoryRepository;
    private final UserRepository userRepository;

    public GithubConnectionService(
            GithubApiClient githubApiClient,
            GithubConnectionRepository connectionRepository,
            GithubRepositoryRepository repositoryRepository,
            UserRepository userRepository
    ) {
        this.githubApiClient = githubApiClient;
        this.connectionRepository = connectionRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * GitHub 연결을 생성하거나 기존 연결을 갱신한다.
     *
     * mode=oauth: accessToken으로 GitHub API 호출 (public + private repo 접근 가능).
     * mode=url:   githubLogin으로 public 정보만 조회 (token 없음).
     *
     * NOTE: GitHub API 호출은 긴 트랜잭션 안에 넣지 않는다 (backend-conventions.md §9.2).
     */
    public GithubConnectionResponse createOrUpdateConnection(Long userId, GithubConnectRequest request) {
        // 1. 사용자 조회 (read-only, 트랜잭션 밖)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 2. GitHub API로 사용자 정보 확인 (트랜잭션 밖 — 외부 API 호출)
        GithubApiClient.GithubUserInfo githubUser = fetchGithubUserInfo(request);

        // 3. GitHub API로 repo 목록 조회 (트랜잭션 밖 — 외부 API 호출)
        List<GithubApiClient.GithubRepoInfo> repos = fetchRepos(request, githubUser.login());

        // 4. DB 저장 (트랜잭션 안 — 외부 호출 결과를 받은 뒤 저장)
        return saveConnectionWithRepos(user, githubUser, repos, request);
    }

    /**
     * GitHub OAuth 로그인 성공 시 토큰과 login만 저장한다 (repo 조회 없음).
     *
     * CustomOAuth2UserService에서 GitHub provider로 로그인할 때 호출한다.
     * 이미 연결이 있으면 token을 갱신하고, 없으면 새 연결 레코드를 생성한다.
     * repo 목록은 사용자가 명시적으로 refreshFromStoredConnection()을 호출할 때 채운다.
     */
    @Transactional
    public void saveConnectionOnly(User user, Long githubUserId, String githubLogin, String token) {
        Instant now = Instant.now();
        connectionRepository.findByUser(user).ifPresentOrElse(
                existing -> existing.update(githubUserId, githubLogin, token, null, now),
                () -> connectionRepository.save(GithubConnection.builder()
                        .user(user)
                        .githubUserId(githubUserId)
                        .githubLogin(githubLogin)
                        .accessToken(token)
                        .syncStatus(GithubSyncStatus.PENDING)
                        .connectedAt(now)
                        .build())
        );
    }

    /**
     * 저장된 연결 정보(token)를 사용해 GitHub API를 호출하고 repo 목록을 갱신한다.
     *
     * GitHub OAuth 로그인 사용자가 repository 목록을 처음 가져올 때 사용한다.
     * GitHub API 호출은 트랜잭션 밖에서 실행한다.
     */
    public GithubConnectionResponse refreshFromStoredConnection(Long userId) {
        // 1. 연결 정보 조회 (트랜잭션 밖)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        GithubConnection connection = connectionRepository.findByUser(user)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "GitHub 연결 정보가 없습니다. 먼저 GitHub 연결을 설정하세요."));

        // 2. 저장된 token으로 repo 목록 조회 (트랜잭션 밖)
        List<GithubApiClient.GithubRepoInfo> repos;
        if (connection.getAccessToken() != null) {
            repos = githubApiClient.getAuthenticatedUserRepos(connection.getAccessToken());
        } else {
            repos = githubApiClient.getPublicRepos(connection.getGithubLogin());
        }

        // 3. DB 저장 (트랜잭션 안)
        GithubApiClient.GithubUserInfo githubUser = new GithubApiClient.GithubUserInfo(
                connection.getGithubUserId(), connection.getGithubLogin());
        GithubConnectRequest refreshRequest = new GithubConnectRequest(
                "oauth", null, connection.getAccessToken(), null);
        return saveConnectionWithRepos(user, githubUser, repos, refreshRequest);
    }

    // ─────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────

    private GithubApiClient.GithubUserInfo fetchGithubUserInfo(GithubConnectRequest request) {
        return switch (request.mode()) {
            case "oauth" -> {
                if (request.accessToken() == null || request.accessToken().isBlank()) {
                    throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                            HttpStatus.BAD_REQUEST, "oauth 모드에서는 accessToken이 필요합니다.");
                }
                // 토큰으로 인증된 사용자 정보 조회
                yield githubApiClient.getAuthenticatedUser(request.accessToken());
            }
            case "url" -> {
                if (request.githubLogin() == null || request.githubLogin().isBlank()) {
                    throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                            HttpStatus.BAD_REQUEST, "url 모드에서는 githubLogin이 필요합니다.");
                }
                // GitHub login 형식 검증: 영문자, 숫자, 하이픈만 허용. 길이 1~39.
                // 하이픈으로 시작하거나 끝나거나 연속 하이픈은 GitHub 정책상 불가.
                if (!request.githubLogin().matches("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$")) {
                    throw new ServiceException(ErrorCode.GITHUB_URL_INVALID,
                            HttpStatus.BAD_REQUEST, "올바르지 않은 GitHub 사용자 이름 형식입니다.");
                }
                // login으로 공개 사용자 정보 조회 (token 없음)
                yield githubApiClient.getPublicUser(request.githubLogin());
            }
            default -> throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST, "지원하지 않는 mode입니다. oauth 또는 url을 사용하세요.");
        };
    }

    private List<GithubApiClient.GithubRepoInfo> fetchRepos(
            GithubConnectRequest request, String login) {
        if ("oauth".equals(request.mode()) && request.accessToken() != null) {
            // OAuth token이 있으면 인증된 사용자의 repo 조회 (private 포함 가능)
            return githubApiClient.getAuthenticatedUserRepos(request.accessToken());
        }
        // url 모드 또는 token 없는 경우: public repo만
        return githubApiClient.getPublicRepos(login);
    }

    /**
     * GitHub 연결 + repo 목록을 한 트랜잭션에 저장한다.
     * 기존 연결이 있으면 갱신, 없으면 생성한다.
     * repo는 (github_connection_id, github_repo_id) unique 제약 기반으로 upsert한다.
     */
    @Transactional
    protected GithubConnectionResponse saveConnectionWithRepos(
            User user,
            GithubApiClient.GithubUserInfo githubUser,
            List<GithubApiClient.GithubRepoInfo> repos,
            GithubConnectRequest request
    ) {
        Instant now = Instant.now();

        // 기존 연결 조회 → 있으면 갱신, 없으면 생성
        GithubConnection connection = connectionRepository.findByUser(user)
                .map(existing -> {
                    log.info("Updating existing GitHub connection for userId={}", user.getId());
                    existing.update(
                            githubUser.id(), githubUser.login(),
                            request.accessToken(), request.accessScope(), now);
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("Creating new GitHub connection for userId={}", user.getId());
                    return connectionRepository.save(GithubConnection.builder()
                            .user(user)
                            .githubUserId(githubUser.id())
                            .githubLogin(githubUser.login())
                            .accessToken(request.accessToken())
                            .accessScope(request.accessScope())
                            .syncStatus(GithubSyncStatus.PENDING)
                            .connectedAt(now)
                            .build());
                });

        // repo 목록 upsert
        // 중복 저장 방지: unique 제약(connection + github_repo_id) 기반으로 검사 후 저장
        for (GithubApiClient.GithubRepoInfo repoInfo : repos) {
            Optional<GithubRepository> existing =
                    repositoryRepository.findByGithubConnectionAndGithubRepoId(connection, repoInfo.id());

            if (existing.isPresent()) {
                // 이미 저장된 repo면 최신 값으로 갱신 (visibility, branch, url 변경 가능)
                existing.get().sync(repoInfo.visibility(), repoInfo.defaultBranch(), repoInfo.htmlUrl(), now);
            } else {
                repositoryRepository.save(GithubRepository.builder()
                        .githubConnection(connection)
                        .githubRepoId(repoInfo.id())
                        .ownerLogin(repoInfo.ownerLogin())
                        .repoName(repoInfo.name())
                        .fullName(repoInfo.fullName())
                        .htmlUrl(repoInfo.htmlUrl())
                        .visibility(repoInfo.visibility())
                        .defaultBranch(repoInfo.defaultBranch())
                        .selected(false)  // 초기값: 미선택
                        .syncedAt(now)
                        .build());
            }
        }

        // 연결 성공 처리
        connection.markSyncSuccess(now);

        return GithubConnectionResponse.from(connection);
    }
}