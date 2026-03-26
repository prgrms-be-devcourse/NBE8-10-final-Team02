package com.back.backend.domain.github.service;

import com.back.backend.domain.github.dto.request.AddContributionByUrlRequest;
import com.back.backend.domain.github.dto.request.SaveContributionRequest;
import com.back.backend.domain.github.dto.response.ContributedRepoResponse;
import com.back.backend.domain.github.dto.response.GithubRepositoryResponse;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.RepositoryVisibility;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub repo 탐색 및 기여 repo 저장.
 *
 * 책임:
 *   - 사용자가 기여한 public repo 목록 조회 (GraphQL, yearsOffset으로 기간 지정)
 *   - 탐색된 기여 repo를 github_repositories에 저장
 *   - URL 직접 입력으로 기여 repo 추가 (본인 커밋 확인 후 저장)
 *
 * 주의:
 *   - GitHub API 호출은 트랜잭션 밖에서 수행한다 (backend-conventions.md §9.2).
 *   - GitHub OAuth 연동이 없는 사용자는 모든 기능이 차단된다.
 */
@Service
public class GithubDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(GithubDiscoveryService.class);
    private static final Pattern GITHUB_REPO_URL_PATTERN =
            Pattern.compile("(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$");

    private final GithubApiClient githubApiClient;
    private final GithubConnectionRepository connectionRepository;
    private final GithubRepositoryRepository repositoryRepository;
    private final UserRepository userRepository;

    public GithubDiscoveryService(
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
     * 사용자가 커밋을 기여한 public repo 목록을 조회한다.
     * GitHub OAuth 토큰이 있어야 호출 가능하다.
     * 이미 github_repositories에 저장된 repo는 alreadySaved=true로 표시한다.
     *
     * @param yearsOffset 0=최근 2년, 1=2~4년 전, 2=4~6년 전
     */
    public List<ContributedRepoResponse> findContributedRepos(Long userId, int yearsOffset) {
        // 1. OAuth 토큰 확인 (트랜잭션 필요)
        GithubConnection connection = getConnectionWithToken(userId);

        // 2. GitHub API 호출 (트랜잭션 밖)
        List<GithubApiClient.GithubContributedRepo> contributed =
                githubApiClient.getContributedRepos(connection.getAccessToken(), yearsOffset);

        if (contributed.isEmpty()) return List.of();

        // 3. 이미 저장된 repo 확인 (readOnly 트랜잭션)
        Set<Long> savedRepoIds = findSavedRepoIds(connection, contributed);

        return contributed.stream()
                .map(repo -> ContributedRepoResponse.from(repo, savedRepoIds.contains(repo.githubRepoId())))
                .toList();
    }

    /**
     * 기여 탐색 목록에서 선택한 repo를 github_repositories에 저장한다.
     * 이미 저장된 경우 기존 레코드를 그대로 반환한다 (중복 저장 방지).
     * defaultBranch는 null로 저장하고, 커밋 동기화 시점에 갱신된다.
     */
    @Transactional
    public GithubRepositoryResponse saveContributionRepo(Long userId, SaveContributionRequest request) {
        GithubConnection connection = getConnectionWithToken(userId);

        // 중복 체크: 이미 저장된 경우 기존 레코드 반환
        Optional<GithubRepository> existing =
                repositoryRepository.findByGithubConnectionAndGithubRepoId(
                        connection, request.githubRepoId());
        if (existing.isPresent()) {
            return GithubRepositoryResponse.from(existing.get());
        }

        // nameWithOwner("owner/repo") 파싱
        String[] parts = request.nameWithOwner().split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ServiceException(ErrorCode.GITHUB_URL_INVALID, HttpStatus.BAD_REQUEST,
                    "유효하지 않은 repository 이름 형식입니다. (owner/repo 형식이어야 합니다)");
        }
        String ownerLogin = parts[0];
        String repoName   = parts[1];

        GithubRepository saved = repositoryRepository.save(
                GithubRepository.builder()
                        .githubConnection(connection)
                        .githubRepoId(request.githubRepoId())
                        .ownerLogin(ownerLogin)
                        .repoName(repoName)
                        .fullName(request.nameWithOwner())
                        .htmlUrl(request.url())
                        .visibility(RepositoryVisibility.PUBLIC)
                        .defaultBranch(null)   // 커밋 동기화 시점에 갱신
                        .selected(false)
                        .pushedAt(null)            // contributionsCollection에는 pushed_at 없음
                        .ownerType("collaborator") // 기여 탭에서 추가한 repo는 항상 collaborator
                        .syncedAt(Instant.now())
                        .repoSizeKb(request.repoSizeKb())
                        .build()
        );

        log.info("Saved contribution repo: {} for userId={}", request.nameWithOwner(), userId);
        return GithubRepositoryResponse.from(saved);
    }

    /**
     * 사용자가 직접 입력한 GitHub URL로 기여 repo를 추가한다.
     * 본인(githubLogin)의 커밋이 1개 이상 존재하는 경우에만 저장된다.
     * 이미 저장된 경우 기존 레코드를 반환한다.
     */
    public GithubRepositoryResponse verifyAndAddContributionByUrl(Long userId, String url) {
        // 1. OAuth 토큰 확인 (트랜잭션 밖)
        GithubConnection connection = getConnectionWithToken(userId);
        String token = connection.getAccessToken();

        // 2. URL 파싱 (트랜잭션 밖)
        ParsedRepoUrl parsed = parseGithubUrl(url);

        // 3. repo 상세 조회 (트랜잭션 밖) — private이면 예외
        GithubApiClient.GithubRepoDetail detail =
                githubApiClient.getRepoDetail(parsed.owner(), parsed.repo(), token);

        // 4. 본인 커밋 확인 (트랜잭션 밖)
        int commitCount = githubApiClient.countUserCommits(
                parsed.owner(), parsed.repo(), connection.getGithubLogin(), token);
        if (commitCount == 0) {
            throw new ServiceException(ErrorCode.GITHUB_USER_COMMIT_NOT_IDENTIFIED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "이 repository에서 본인의 커밋을 찾을 수 없습니다.");
        }

        // 5. 저장 (트랜잭션 안)
        return saveVerifiedRepo(connection, detail);
    }

    // ─────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    protected GithubConnection getConnectionWithToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "사용자를 찾을 수 없습니다."));
        GithubConnection connection = connectionRepository.findByUser(user)
                .orElseThrow(() -> new ServiceException(ErrorCode.GITHUB_SCOPE_INSUFFICIENT, HttpStatus.FORBIDDEN,
                        "GitHub OAuth 연동이 필요합니다."));
        if (connection.getAccessToken() == null) {
            throw new ServiceException(ErrorCode.GITHUB_SCOPE_INSUFFICIENT, HttpStatus.FORBIDDEN,
                    "GitHub OAuth 연동이 필요합니다.");
        }
        return connection;
    }

    @Transactional(readOnly = true)
    protected Set<Long> findSavedRepoIds(GithubConnection connection,
                                         List<GithubApiClient.GithubContributedRepo> contributed) {
        List<Long> ids = contributed.stream()
                .map(GithubApiClient.GithubContributedRepo::githubRepoId)
                .toList();
        return repositoryRepository.findGithubRepoIdsByGithubConnectionAndGithubRepoIdIn(connection, ids);
    }

    @Transactional
    protected GithubRepositoryResponse saveVerifiedRepo(
            GithubConnection connection, GithubApiClient.GithubRepoDetail detail) {

        Optional<GithubRepository> existing =
                repositoryRepository.findByGithubConnectionAndGithubRepoId(
                        connection, detail.id());
        if (existing.isPresent()) {
            return GithubRepositoryResponse.from(existing.get());
        }

        GithubRepository saved = repositoryRepository.save(
                GithubRepository.builder()
                        .githubConnection(connection)
                        .githubRepoId(detail.id())
                        .ownerLogin(detail.ownerLogin())
                        .repoName(detail.repoName())
                        .fullName(detail.nameWithOwner())
                        .htmlUrl(detail.url())
                        .visibility(RepositoryVisibility.PUBLIC)
                        .defaultBranch(detail.defaultBranch())
                        .selected(false)
                        .pushedAt(null)            // URL 추가 경로에는 pushed_at 없음
                        .ownerType("collaborator") // URL로 추가한 repo도 항상 collaborator
                        .syncedAt(Instant.now())
                        .repoSizeKb(detail.sizeKb())
                        .build()
        );

        log.info("Saved contribution repo by URL: {} for userId={}",
                detail.nameWithOwner(), connection.getUser().getId());
        return GithubRepositoryResponse.from(saved);
    }

    private ParsedRepoUrl parseGithubUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ServiceException(ErrorCode.GITHUB_URL_INVALID, HttpStatus.BAD_REQUEST,
                    "유효하지 않은 GitHub repo URL입니다.");
        }
        Matcher matcher = GITHUB_REPO_URL_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            throw new ServiceException(ErrorCode.GITHUB_URL_INVALID, HttpStatus.BAD_REQUEST,
                    "유효하지 않은 GitHub repo URL입니다. (예: https://github.com/owner/repo)");
        }
        return new ParsedRepoUrl(matcher.group(1), matcher.group(2));
    }

    private record ParsedRepoUrl(String owner, String repo) {}
}
