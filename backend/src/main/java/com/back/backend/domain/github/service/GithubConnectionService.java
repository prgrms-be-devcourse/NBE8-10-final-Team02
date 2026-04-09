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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    // @Transactional이 붙은 메서드(saveConnectionWithRepos)를 같은 클래스 내에서 호출하면
    // Spring AOP 프록시를 우회해 트랜잭션이 적용되지 않는 자기호출(self-invocation) 문제가 생긴다.
    // @Lazy 셀프 주입으로 프록시를 통해 호출해 이를 해결한다.
    @Lazy
    @Autowired
    private GithubConnectionService self;

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
     * Google/Kakao 로그인 사용자도 GitHub 기능 사용 시 OAuth 연동이 필요하다.
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

        // 4. DB 저장 (트랜잭션 안 — self 프록시를 통해 @Transactional 적용)
        return self.saveConnectionWithRepos(user, githubUser, repos, request);
    }

    /**
     * 현재 사용자의 GitHub 연결 정보를 반환한다.
     * 연결이 없으면 null을 반환한다 (예외를 던지지 않음).
     *
     * 프론트엔드 /portfolio/github 페이지 진입 시 이미 연결되어 있는지 확인하는 데 사용한다.
     * Google/Kakao 로그인 사용자도 이전에 URL 모드로 연동했다면 여기서 감지된다.
     */
    public GithubConnectionResponse getConnectionOrNull(Long userId) {
        return userRepository.findById(userId)
                .flatMap(connectionRepository::findByUser)
                .map(GithubConnectionResponse::from)
                .orElse(null);
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

        Optional<GithubConnection> byUser = connectionRepository.findByUser(user);
        Optional<GithubConnection> byGithubId = connectionRepository.findByGithubUserId(githubUserId);

        // github_user_id가 다른 app 사용자에게 이미 연결된 경우 → 차단
        if (byGithubId.isPresent() && !byGithubId.get().getUser().getId().equals(user.getId())) {
            throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.CONFLICT,
                    "이 GitHub 계정은 이미 다른 사용자와 연결되어 있습니다. " +
                    "다른 GitHub 계정을 사용하거나, 해당 계정으로 로그인하세요.");
        }

        byUser.or(() -> byGithubId)
                .ifPresentOrElse(
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

        // 2. GitHub OAuth 연동 여부 확인
        if (connection.getAccessToken() == null) {
            throw new ServiceException(ErrorCode.GITHUB_SCOPE_INSUFFICIENT, HttpStatus.FORBIDDEN,
                    "GitHub OAuth 연동이 필요합니다. GitHub 계정을 연동해주세요.");
        }

        // 3. 저장된 token으로 repo 목록 조회 (트랜잭션 밖)
        List<GithubApiClient.GithubRepoInfo> repos =
                githubApiClient.getAuthenticatedUserRepos(connection.getAccessToken());

        // 4. DB 저장 (트랜잭션 안 — self 프록시를 통해 @Transactional 적용)
        GithubApiClient.GithubUserInfo githubUser = new GithubApiClient.GithubUserInfo(
                connection.getGithubUserId(), connection.getGithubLogin());
        GithubConnectRequest refreshRequest = new GithubConnectRequest(
                "oauth", null, connection.getAccessToken(), null);
        return self.saveConnectionWithRepos(user, githubUser, repos, refreshRequest);
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
            default -> throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST, "지원하지 않는 mode입니다. GitHub OAuth를 통해 연동해주세요.");
        };
    }

    private List<GithubApiClient.GithubRepoInfo> fetchRepos(
            GithubConnectRequest request) {
        // OAuth token으로 인증된 사용자의 repo 조회 (private 포함 가능)
        return githubApiClient.getAuthenticatedUserRepos(request.accessToken());
    }

    /**
     * GitHub 연결 + repo 목록을 한 트랜잭션에 저장한다.
     * 기존 연결이 있으면 갱신, 없으면 생성한다.
     * repo는 (github_connection_id, github_repo_id) unique 제약 기반으로 upsert한다.
     */
    @Transactional
    public GithubConnectionResponse saveConnectionWithRepos(
            User user,
            GithubApiClient.GithubUserInfo githubUser,
            List<GithubApiClient.GithubRepoInfo> repos,
            GithubConnectRequest request
    ) {
        Instant now = Instant.now();

        // 두 축으로 별도 조회:
        //   byUser    — 현재 app 사용자의 기존 연결 행 (있으면 갱신 대상)
        //   byGithubId — 연결하려는 GitHub 계정이 이미 어딘가에 연결된 행
        // .or() 체인을 쓰면 byUser가 있을 때 byGithubId를 아예 조회하지 않아
        // "다른 사용자가 점유한 github_user_id를 덮어쓰기" 하는 unique 제약 위반이 발생한다.
        Optional<GithubConnection> byUser = connectionRepository.findByUser(user);
        Optional<GithubConnection> byGithubId = connectionRepository.findByGithubUserId(githubUser.id());

        // github_user_id가 다른 app 사용자에게 이미 연결된 경우 → 차단
        if (byGithubId.isPresent() && !byGithubId.get().getUser().getId().equals(user.getId())) {
            throw new ServiceException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.CONFLICT,
                    "이 GitHub 계정은 이미 다른 사용자와 연결되어 있습니다. " +
                    "다른 GitHub 계정을 사용하거나, 해당 계정으로 로그인하세요.");
        }

        GithubConnection connection = byUser.or(() -> byGithubId)
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
        String githubLogin = connection.getGithubLogin();
        for (GithubApiClient.GithubRepoInfo repoInfo : repos) {
            // ownerLogin == 사용자 githubLogin → "owner", 그 외(org, 협업 repo) → "collaborator"
            String ownerType = githubLogin.equalsIgnoreCase(repoInfo.ownerLogin()) ? "owner" : "collaborator";

            Optional<GithubRepository> existing =
                    repositoryRepository.findByGithubConnectionAndGithubRepoId(connection, repoInfo.id());

            if (existing.isPresent()) {
                existing.get().sync(repoInfo.visibility(), repoInfo.defaultBranch(), repoInfo.htmlUrl(),
                        repoInfo.pushedAt(), ownerType, repoInfo.language(), now);
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
                        .selected(false)
                        .pushedAt(repoInfo.pushedAt())
                        .ownerType(ownerType)
                        .language(repoInfo.language())
                        .syncedAt(now)
                        .build());
            }
        }

        // 연결 성공 처리
        connection.markSyncSuccess(now);

        return GithubConnectionResponse.from(connection);
    }
}
