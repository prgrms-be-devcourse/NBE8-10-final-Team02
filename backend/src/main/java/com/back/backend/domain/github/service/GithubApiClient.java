package com.back.backend.domain.github.service;

import com.back.backend.domain.github.entity.RepositoryVisibility;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GitHub REST API를 호출하는 클라이언트.
 *
 * 책임:
 *   - GitHub API HTTP 호출
 *   - 응답을 도메인 친화적 타입으로 매핑
 *   - rate limit / scope 오류를 ServiceException으로 변환
 *
 * 주의:
 *   - 이 클래스의 모든 메서드는 트랜잭션 밖에서 호출해야 한다 (backend-conventions.md §9.2).
 *   - access token은 절대 로그에 남기지 않는다 (§12.3).
 */
@Service
public class GithubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GithubApiClient.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestClient restClient;

    public GithubApiClient() {
        // RestClient.Builder를 Spring 컨텍스트에서 주입받지 않고 직접 생성한다.
        // spring-boot-starter-webmvc 환경에서 RestClient.Builder 자동 구성이 없을 수 있기 때문이다.
        this.restClient = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                // GitHub API 요청 시 필수 헤더
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "interview-platform/1.0")
                .build();
    }

    // ─────────────────────────────────────────────────
    // 도메인 출력 타입 (GitHub API 응답을 서비스에 전달하는 단순 레코드)
    // ─────────────────────────────────────────────────

    public record GithubUserInfo(Long id, String login) {}

    public record GithubRepoInfo(
            Long id,
            String name,
            String fullName,
            String htmlUrl,
            RepositoryVisibility visibility,
            String defaultBranch,
            String ownerLogin,
            Instant pushedAt,  // GitHub pushed_at. null 가능 (URL 모드 등)
            String language    // primary language. null 가능
    ) {}

    public record GithubCommitInfo(
            String sha,
            String message,
            String authorLogin,  // GitHub 계정이 없는 경우 null
            String authorName,
            String authorEmail,
            Instant committedAt
    ) {}

    /** validatePublicRepo 결과 */
    public record GithubRepoPublicInfo(
            Long githubRepoId,
            String nameWithOwner,
            String url,
            String language,      // null 가능
            Integer repoSizeKb    // null 가능
    ) {}

    /** getRepoDetail 결과 — URL 기여 추가 시 repo 상세 정보 */
    public record GithubRepoDetail(
            Long id,
            String nameWithOwner,
            String ownerLogin,
            String repoName,
            String url,
            String defaultBranch, // null 가능
            Integer sizeKb        // null 가능
    ) {}

    /** getContributedRepos 결과 단건 */
    public record GithubContributedRepo(
            Long githubRepoId,
            String nameWithOwner,
            String url,
            String language,      // null 가능
            Integer repoSizeKb,   // null 가능
            int contributionCount
    ) {}

    // ─────────────────────────────────────────────────
    // GitHub API 응답 파싱용 내부 레코드 (Jackson 역직렬화 전용)
    // ─────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubUserResponse(Long id, String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubRepoResponse(
            Long id,
            String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("private") boolean privateRepo,
            @JsonProperty("default_branch") String defaultBranch,
            GitHubRepoOwner owner,
            @JsonProperty("pushed_at") Instant pushedAt,  // 마지막 push 시각
            String language   // primary language. null 가능
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubRepoOwner(String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubCommitResponse(
            String sha,
            GitHubCommitDetail commit,
            GitHubCommitUser author  // GitHub 계정이 없는 commit이면 null
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubCommitDetail(String message, GitHubCommitDetailAuthor author) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubCommitDetailAuthor(String name, String email, Instant date) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubCommitUser(String login) {}

    // validatePublicRepo / getRepoDetail 공통 REST 응답
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubRepoDetailResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("private") boolean privateRepo,
            @JsonProperty("size") Integer sizeKb,
            @JsonProperty("default_branch") String defaultBranch,
            GitHubRepoOwner owner,            // 기존 GitHubRepoOwner 재사용
            GitHubRepoDetailLanguage language
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubRepoDetailLanguage(String name) {}

    // countUserCommits 용 단건 응답 (sha만 필요)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubCommitShaResponse(String sha) {}

    // GraphQL contributionsCollection 응답 파싱
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLResponse(GraphQLData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLData(GraphQLViewer viewer) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLViewer(GraphQLContributions contributionsCollection) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLContributions(
            List<GraphQLRepoContribution> commitContributionsByRepository
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLRepoContribution(
            GraphQLRepository repository,
            GraphQLContributionCount contributions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLRepository(
            String nameWithOwner,
            String url,
            boolean isPrivate,
            Long databaseId,
            Integer diskUsage,          // KB 단위
            GraphQLLanguage primaryLanguage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLLanguage(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphQLContributionCount(int totalCount) {}

    // ─────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────

    /**
     * OAuth access token으로 인증된 GitHub 사용자 정보를 조회한다.
     * oauth 모드에서 연결 생성 시 사용한다.
     */
    public GithubUserInfo getAuthenticatedUser(String token) {
        // 토큰 값은 로그에 남기지 않는다
        log.info("Fetching authenticated GitHub user info");
        try {
            GitHubUserResponse response = restClient.get()
                    .uri("/user")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(status -> status.value() == 401,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_PROFILE_NOT_FOUND, HttpStatus.BAD_REQUEST,
                                    "유효하지 않은 GitHub 토큰입니다."); })
                    .onStatus(status -> status.value() == 403 || status.value() == 429,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS,
                                    "GitHub API 요청 한도를 초과했습니다."); })
                    .body(GitHubUserResponse.class);

            if (response == null) {
                throw new ServiceException(ErrorCode.GITHUB_PROFILE_NOT_FOUND,
                        HttpStatus.BAD_GATEWAY, "GitHub 사용자 정보를 가져올 수 없습니다.");
            }
            return new GithubUserInfo(response.id(), response.login());

        } catch (ServiceException e) {
            throw e;
        } catch (RestClientException e) {
            // 토큰 값이 로그에 노출되지 않도록 메시지만 기록
            log.warn("GitHub API call failed: GET /user, reason: {}", e.getMessage());
            throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE, "GitHub API 호출에 실패했습니다.");
        }
    }

    /**
     * GitHub login으로 공개 사용자 정보를 조회한다.
     * url 모드에서 login 유효성 확인 시 사용한다 (token 없이 public 조회).
     */
    public GithubUserInfo getPublicUser(String login) {
        log.info("Fetching public GitHub user info for login: {}", login);
        try {
            GitHubUserResponse response = restClient.get()
                    .uri("/users/{login}", login)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_PROFILE_NOT_FOUND, HttpStatus.NOT_FOUND,
                                    "존재하지 않는 GitHub 사용자입니다."); })
                    .onStatus(status -> status.value() == 403 || status.value() == 429,
                            (req, res) -> {
                                // url 모드(비인증)에서 rate limit 초과 시 도달하는 경로
                                // 비인증 요청은 IP당 60 req/hour 제한이 있다
                                String remaining = res.getHeaders().getFirst("X-RateLimit-Remaining");
                                String resetAt = res.getHeaders().getFirst("X-RateLimit-Reset");
                                String msg = "GitHub API 요청 한도를 초과했습니다. "
                                        + "GitHub 계정을 OAuth로 연동하면 요청 한도가 늘어납니다."
                                        + (resetAt != null ? " 초기화 시각(unix): " + resetAt : "");
                                throw new ServiceException(ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED,
                                        HttpStatus.TOO_MANY_REQUESTS, msg);
                            })
                    .body(GitHubUserResponse.class);

            if (response == null) {
                throw new ServiceException(ErrorCode.GITHUB_PROFILE_NOT_FOUND,
                        HttpStatus.BAD_GATEWAY, "GitHub 사용자 정보를 가져올 수 없습니다.");
            }
            return new GithubUserInfo(response.id(), response.login());

        } catch (ServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("GitHub API call failed: GET /users/{}, reason: {}", login, e.getMessage());
            throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE, "GitHub API 호출에 실패했습니다.");
        }
    }

    /**
     * OAuth token으로 인증된 사용자의 repo 목록을 가져온다.
     * oauth 모드용. public repository만 반환한다.
     * visibility=public 파라미터로 GitHub API 수준에서 먼저 필터링하고,
     * 응답에 private이 섞이는 경우를 대비해 서비스 레벨에서도 필터링한다.
     */
    public List<GithubRepoInfo> getAuthenticatedUserRepos(String token) {
        log.info("Fetching public repos for authenticated GitHub user");
        // token은 로그에 남기지 않는다
        List<GitHubRepoResponse> raw = fetchAllPages(
                "/user/repos?visibility=public&per_page=100", token, GitHubRepoResponse[].class);
        return raw.stream()
                .filter(r -> !r.privateRepo())   // 안전망: API 응답에서 private 제거
                .map(r -> new GithubRepoInfo(
                        r.id(), r.name(), r.fullName(), r.htmlUrl(),
                        RepositoryVisibility.PUBLIC,
                        r.defaultBranch(),
                        r.owner() != null ? r.owner().login() : null,
                        r.pushedAt(),
                        r.language()
                ))
                .toList();
    }

    /**
     * 특정 GitHub login의 public repo 목록을 가져온다.
     * url 모드용. private repo는 포함되지 않는다.
     */
    public List<GithubRepoInfo> getPublicRepos(String login) {
        log.info("Fetching public repos for GitHub user: {}", login);
        List<GitHubRepoResponse> raw = fetchAllPages(
                "/users/" + login + "/repos?per_page=100", null, GitHubRepoResponse[].class);
        return raw.stream()
                .map(r -> new GithubRepoInfo(
                        r.id(), r.name(), r.fullName(), r.htmlUrl(),
                        RepositoryVisibility.PUBLIC,
                        r.defaultBranch(),
                        r.owner() != null ? r.owner().login() : null,
                        r.pushedAt(),
                        r.language()
                ))
                .toList();
    }

    /**
     * 특정 repo에서 지정한 author의 commit 목록을 가져온다.
     * GitHub API의 author 파라미터로 본인 커밋만 필터링한다.
     * private repo 접근 시 token에 repo scope가 필요하다.
     */
    public List<GithubCommitInfo> getCommits(String owner, String repo, String authorLogin, String token) {
        log.info("Fetching commits for repo: {}/{}, author: {}", owner, repo, authorLogin);
        String path = "/repos/" + owner + "/" + repo + "/commits?author=" + authorLogin + "&per_page=100";
        List<GitHubCommitResponse> raw = fetchAllPages(path, token, GitHubCommitResponse[].class);
        return raw.stream()
                .map(c -> new GithubCommitInfo(
                        c.sha(),
                        c.commit() != null ? c.commit().message() : "",
                        c.author() != null ? c.author().login() : null,
                        c.commit() != null && c.commit().author() != null ? c.commit().author().name() : null,
                        c.commit() != null && c.commit().author() != null ? c.commit().author().email() : null,
                        c.commit() != null && c.commit().author() != null ? c.commit().author().date() : null
                ))
                .toList();
    }

    /**
     * GitHub URL이 public repo인지 검증하고 기본 정보를 반환한다.
     * token이 없으면 비인증 요청(60 req/h)으로 시도한다.
     * private repo이거나 존재하지 않으면 GITHUB_REPOSITORY_FORBIDDEN을 던진다.
     */
    public GithubRepoPublicInfo validatePublicRepo(String owner, String repo, String token) {
        log.info("Validating public repo: {}/{}", owner, repo);
        try {
            var builder = restClient.get().uri("/repos/{owner}/{repo}", owner, repo);
            var reqSpec = (token != null)
                    ? builder.header("Authorization", "Bearer " + token)
                    : builder;

            GitHubRepoDetailResponse response = reqSpec
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_REPOSITORY_FORBIDDEN, HttpStatus.FORBIDDEN,
                                    "비공개 repo이거나 존재하지 않는 repo입니다."); })
                    .onStatus(status -> status.value() == 403 || status.value() == 429,
                            (req, res) -> {
                                String remaining = res.getHeaders().getFirst("X-RateLimit-Remaining");
                                if ("0".equals(remaining)) {
                                    throw new ServiceException(ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED,
                                            HttpStatus.TOO_MANY_REQUESTS,
                                            "GitHub API 요청 한도를 초과했습니다. GitHub 계정을 연동하면 한도가 늘어납니다.");
                                }
                                throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN,
                                        HttpStatus.FORBIDDEN, "비공개 repo이거나 존재하지 않는 repo입니다.");
                            })
                    .body(GitHubRepoDetailResponse.class);

            if (response == null) {
                throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN,
                        HttpStatus.FORBIDDEN, "repo 정보를 가져올 수 없습니다.");
            }
            if (response.privateRepo()) {
                throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN,
                        HttpStatus.FORBIDDEN, "비공개 repo이거나 존재하지 않는 repo입니다.");
            }

            return new GithubRepoPublicInfo(
                    response.id(),
                    response.fullName(),
                    response.htmlUrl(),
                    response.language() != null ? response.language().name() : null,
                    response.sizeKb()
            );

        } catch (ServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("GitHub API call failed: GET /repos/{}/{}, reason: {}", owner, repo, e.getMessage());
            throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE, "GitHub API 호출에 실패했습니다.");
        }
    }

    /**
     * GitHub OAuth 토큰으로 사용자가 커밋을 기여한 public repo 목록을 조회한다.
     * GraphQL contributionsCollection을 사용하며, OAuth 토큰이 없으면 호출할 수 없다.
     * isPrivate=true인 repo는 결과에서 제외한다.
     *
     * ── GitHub API 제약 ──────────────────────────────────────────────────
     * contributionsCollection의 from~to 범위는 최대 1년(365일)이다.
     * 초과하면 에러 없이 조용히 1년치만 잘라서 반환하므로,
     * yearsOffset 단위도 1년으로 맞춰 한 번의 API 호출로 정확히 1년치를 가져온다.
     * ────────────────────────────────────────────────────────────────────
     *
     * @param yearsOffset 0=최근 1년, 1=1~2년 전, 2=2~3년 전
     */
    public List<GithubContributedRepo> getContributedRepos(String token, int yearsOffset) {
        log.info("Fetching contributed repos via GraphQL, yearsOffset={}", yearsOffset);

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        // yearsOffset=0 → [now-1년, now], yearsOffset=1 → [now-2년, now-1년], ...
        ZonedDateTime to   = now.minusYears(yearsOffset);
        ZonedDateTime from = now.minusYears(yearsOffset + 1);

        return fetchContributionsInRange(token, from, to);
    }

    /**
     * contributionsCollection을 1년 이하 구간으로 호출한다.
     * GitHub API 제약상 from~to는 반드시 1년 이내여야 한다.
     */
    private List<GithubContributedRepo> fetchContributionsInRange(
            String token, ZonedDateTime from, ZonedDateTime to) {

        String fromStr = from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String toStr   = to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String query = """
                query($from: DateTime!, $to: DateTime!) {
                  viewer {
                    contributionsCollection(from: $from, to: $to) {
                      commitContributionsByRepository {
                        repository {
                          nameWithOwner
                          url
                          isPrivate
                          databaseId
                          diskUsage
                          primaryLanguage { name }
                        }
                        contributions { totalCount }
                      }
                    }
                  }
                }
                """;

        try {
            GraphQLResponse response = restClient.post()
                    .uri("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "variables", Map.of("from", fromStr, "to", toStr)))
                    .retrieve()
                    .onStatus(status -> status.value() == 401,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_SCOPE_INSUFFICIENT, HttpStatus.FORBIDDEN,
                                    "기여 repo 탐색은 GitHub OAuth 연동이 필요합니다."); })
                    .onStatus(status -> status.value() == 403 || status.value() == 429,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS,
                                    "GitHub API 요청 한도를 초과했습니다."); })
                    .body(GraphQLResponse.class);

            if (response == null || response.data() == null
                    || response.data().viewer() == null
                    || response.data().viewer().contributionsCollection() == null) {
                return List.of();
            }

            List<GraphQLRepoContribution> contributions =
                    response.data().viewer().contributionsCollection().commitContributionsByRepository();
            if (contributions == null) return List.of();

            return contributions.stream()
                    .filter(c -> c.repository() != null && !c.repository().isPrivate())
                    .map(c -> new GithubContributedRepo(
                            c.repository().databaseId(),
                            c.repository().nameWithOwner(),
                            c.repository().url(),
                            c.repository().primaryLanguage() != null
                                    ? c.repository().primaryLanguage().name() : null,
                            c.repository().diskUsage(),
                            c.contributions() != null ? c.contributions().totalCount() : 0
                    ))
                    .toList();

        } catch (ServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("GitHub GraphQL call failed (from={}, to={}): {}", fromStr, toStr, e.getMessage());
            throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE, "GitHub API 호출에 실패했습니다.");
        }
    }

    /**
     * 특정 public repo의 상세 정보를 조회한다.
     * URL 기반 기여 추가 시 ownerLogin, defaultBranch를 가져오는 데 사용한다.
     * private repo이거나 존재하지 않으면 GITHUB_REPOSITORY_FORBIDDEN을 던진다.
     */
    public GithubRepoDetail getRepoDetail(String owner, String repo, String token) {
        log.info("Fetching repo detail: {}/{}", owner, repo);
        try {
            var builder = restClient.get().uri("/repos/{owner}/{repo}", owner, repo);
            var reqSpec = (token != null)
                    ? builder.header("Authorization", "Bearer " + token)
                    : builder;

            GitHubRepoDetailResponse response = reqSpec
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_REPOSITORY_FORBIDDEN, HttpStatus.FORBIDDEN,
                                    "비공개 repo이거나 존재하지 않는 repo입니다."); })
                    .onStatus(status -> status.value() == 403 || status.value() == 429,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS,
                                    "GitHub API 요청 한도를 초과했습니다."); })
                    .body(GitHubRepoDetailResponse.class);

            if (response == null || response.privateRepo()) {
                throw new ServiceException(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN,
                        HttpStatus.FORBIDDEN, "비공개 repo이거나 존재하지 않는 repo입니다.");
            }

            String ownerLogin = response.owner() != null ? response.owner().login() : owner;
            String repoName   = response.fullName() != null
                    ? response.fullName().contains("/")
                        ? response.fullName().substring(response.fullName().indexOf('/') + 1)
                        : response.fullName()
                    : repo;

            return new GithubRepoDetail(
                    response.id(),
                    response.fullName(),
                    ownerLogin,
                    repoName,
                    response.htmlUrl(),
                    response.defaultBranch(),
                    response.sizeKb()
            );
        } catch (ServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("GitHub API call failed: GET /repos/{}/{}, reason: {}", owner, repo, e.getMessage());
            throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE, "GitHub API 호출에 실패했습니다.");
        }
    }

    /**
     * 특정 repo에서 authorLogin의 커밋이 1개 이상 있는지 확인한다.
     * per_page=1로 최소 요청만 보내고, 배열이 비어 있으면 0을 반환한다.
     */
    public int countUserCommits(String owner, String repo, String authorLogin, String token) {
        log.info("Checking user commits: {}/{}, author: {}", owner, repo, authorLogin);
        try {
            var builder = restClient.get()
                    .uri("/repos/{owner}/{repo}/commits?author={author}&per_page=1",
                            owner, repo, authorLogin);
            var reqSpec = (token != null)
                    ? builder.header("Authorization", "Bearer " + token)
                    : builder;

            GitHubCommitShaResponse[] response = reqSpec
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_REPOSITORY_FORBIDDEN, HttpStatus.FORBIDDEN,
                                    "비공개 repo이거나 존재하지 않는 repo입니다."); })
                    .onStatus(status -> status.value() == 403 || status.value() == 429,
                            (req, res) -> { throw new ServiceException(
                                    ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS,
                                    "GitHub API 요청 한도를 초과했습니다."); })
                    .body(GitHubCommitShaResponse[].class);

            return (response != null) ? response.length : 0;
        } catch (ServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("GitHub commit check failed: {}/{}, author: {}, reason: {}",
                    owner, repo, authorLogin, e.getMessage());
            throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE, "GitHub API 호출에 실패했습니다.");
        }
    }

    // ─────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────

    /**
     * GitHub의 Link 헤더 기반 페이지네이션을 처리한다.
     * 다음 페이지 URL이 없을 때까지 반복 호출하여 전체 결과를 반환한다.
     *
     * @param initialPath 첫 번째 요청 path (또는 full URL)
     * @param token       Bearer token. null이면 인증 없이 호출 (public API 전용).
     * @param arrayType   역직렬화할 배열 타입 (예: GitHubRepoResponse[].class)
     */
    private <T> List<T> fetchAllPages(String initialPath, String token, Class<T[]> arrayType) {
        List<T> all = new ArrayList<>();
        String nextUrl = initialPath;

        while (nextUrl != null) {
            try {
                RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(nextUrl);

                // token이 있을 때만 Authorization 헤더를 추가한다.
                // null이면 헤더 자체를 생략해야 한다. 빈 문자열을 보내면 GitHub이 400을 반환할 수 있다.
                var reqSpec = (token != null)
                        ? spec.header("Authorization", "Bearer " + token)
                        : spec;

                ResponseEntity<T[]> response = reqSpec.retrieve()
                        .onStatus(status -> status.value() == 403,
                                (req, res) -> {
                                    // 403은 rate limit 초과와 scope 부족 두 경우 모두 해당된다.
                                    // X-RateLimit-Remaining: 0 이면 rate limit, 없으면 scope 부족.
                                    String remaining = res.getHeaders().getFirst("X-RateLimit-Remaining");
                                    if ("0".equals(remaining)) {
                                        // rate limit 초과: 언제 풀리는지 Retry-After 또는 X-RateLimit-Reset 제공
                                        String resetAt = res.getHeaders().getFirst("X-RateLimit-Reset");
                                        String msg = "GitHub API 요청 한도를 초과했습니다."
                                                + (resetAt != null ? " 초기화 시각(unix): " + resetAt : "");
                                        throw new ServiceException(ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED,
                                                HttpStatus.TOO_MANY_REQUESTS, msg);
                                    }
                                    // scope 부족: private repo 접근 권한 없음
                                    throw new ServiceException(ErrorCode.GITHUB_SCOPE_INSUFFICIENT,
                                            HttpStatus.FORBIDDEN, "private repository 조회 권한이 부족합니다.");
                                })
                        .onStatus(status -> status.value() == 429,
                                (req, res) -> {
                                    // 429: GitHub Secondary Rate Limit (짧은 시간 내 과다 요청)
                                    String retryAfter = res.getHeaders().getFirst("Retry-After");
                                    String msg = "GitHub API 요청 빈도 제한을 초과했습니다."
                                            + (retryAfter != null ? " " + retryAfter + "초 후 재시도하세요." : "");
                                    throw new ServiceException(ErrorCode.GITHUB_RATE_LIMIT_EXCEEDED,
                                            HttpStatus.TOO_MANY_REQUESTS, msg);
                                })
                        .toEntity(arrayType);

                if (response.getBody() != null) {
                    all.addAll(Arrays.asList(response.getBody()));
                }

                // Link 헤더에서 다음 페이지 URL 추출
                nextUrl = extractNextUrl(response.getHeaders().getFirst("Link"));

            } catch (ServiceException e) {
                throw e;
            } catch (RestClientException e) {
                log.warn("GitHub API pagination call failed for path: {}, reason: {}", nextUrl, e.getMessage());
                throw new ServiceException(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE, "GitHub API 호출에 실패했습니다.");
            }
        }

        return all;
    }

    /**
     * GitHub Link 헤더에서 rel="next" URL을 파싱한다.
     *
     * 형식 예시:
     *   Link: <https://api.github.com/repos/.../commits?page=2>; rel="next",
     *         <https://api.github.com/repos/.../commits?page=5>; rel="last"
     *
     * @return 다음 페이지 URL, 없으면 null
     */
    private String extractNextUrl(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) return null;

        for (String part : linkHeader.split(",")) {
            String[] segments = part.trim().split(";");
            if (segments.length == 2 && segments[1].trim().equals("rel=\"next\"")) {
                // < URL > 형식에서 꺾쇠 제거
                return segments[0].trim().replaceAll("[<>]", "");
            }
        }
        return null;
    }
}