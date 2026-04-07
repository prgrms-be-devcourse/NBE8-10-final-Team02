package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.metadata.dto.CollectedIssue;
import com.back.backend.domain.github.metadata.dto.CollectedPullRequest;
import com.back.backend.domain.github.metadata.dto.GitHubActivitySummary;
import com.back.backend.domain.github.metadata.dto.GitHubActivitySummary.CollectionMeta;
import com.back.backend.domain.github.service.GithubApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * GitHub 메타데이터(Issue, PR, Review) 수집 및 병합의 진입점.
 *
 * <h3>호출 흐름</h3>
 * <pre>
 * ① Rate Limit Guard 확인
 * ② Phase1 Issue 수집 (Time-bound + Hard Cap 페이징)
 * ③ Phase1 PR 수집 (Time-bound + Hard Cap 페이징)
 * ④ Issue Impact Score → Phase2 Issue 상세 조회
 * ⑤ PR Impact Score → Phase2 PR 상세 조회
 * ⑥ 텍스트 정제 (TextSanitizer)
 * ⑦ 토큰 예산 집행 (MetadataTokenBudgetEnforcer)
 * ⑧ GitHubActivitySummary 반환
 * </pre>
 *
 * <h3>부분 실패 정책</h3>
 * GitHub 메타데이터는 보완 데이터이므로 수집 실패가 전체 파이프라인을 중단시키지 않는다.
 * 실패한 섹션은 빈 리스트로 처리하고 {@code isPartial=true}를 기록한다.
 *
 * <p>이 서비스는 외부 API를 호출하므로 {@code @Transactional} 을 사용하지 않는다.
 * (backend-conventions.md §9.2)
 */
@Service
public class GitHubMetadataService {

    private static final Logger log = LoggerFactory.getLogger(GitHubMetadataService.class);

    private final GitHubRateLimitGuard rateLimitGuard;
    private final GitHubIssueCollector issueCollector;
    private final GitHubPullRequestCollector pullRequestCollector;
    private final MetadataTokenBudgetEnforcer tokenBudgetEnforcer;
    private final GithubApiClient githubApiClient;

    public GitHubMetadataService(
            GitHubRateLimitGuard rateLimitGuard,
            GitHubIssueCollector issueCollector,
            GitHubPullRequestCollector pullRequestCollector,
            MetadataTokenBudgetEnforcer tokenBudgetEnforcer,
            com.back.backend.domain.github.service.GithubApiClient githubApiClient
    ) {
        this.rateLimitGuard = rateLimitGuard;
        this.issueCollector = issueCollector;
        this.pullRequestCollector = pullRequestCollector;
        this.tokenBudgetEnforcer = tokenBudgetEnforcer;
        this.githubApiClient = githubApiClient;
    }

    /**
     * 지정된 repo의 GitHub 메타데이터를 수집하고 토큰 예산 내로 정제하여 반환한다.
     *
     * @param userId            서비스 사용자 ID (Rate Limit 캐시 키)
     * @param githubLogin       GitHub 계정 로그인명
     * @param repoNameWithOwner "owner/repo" 형식
     * @param accessToken       github_connections의 OAuth 토큰 (로그에 남기지 않음)
     * @param commitTitles      기존 커밋 제목 목록 (PR title 중복 탐지용)
     * @return 토큰 예산 적용 완료된 GitHub 활동 요약
     */
    public GitHubActivitySummary collectAndMerge(
            long userId,
            String githubLogin,
            String repoNameWithOwner,
            String accessToken,
            List<String> commitTitles
    ) {
        String cacheKey = String.valueOf(userId);
        String[] parts = repoNameWithOwner.split("/", 2);
        String owner = parts[0];
        String repoName = parts.length > 1 ? parts[1] : parts[0];

        // ① Rate Limit 확인 — 부족하면 ServiceException(GITHUB_RATE_LIMIT_EXCEEDED)
        rateLimitGuard.checkGraphQlLimit(accessToken, cacheKey);

        List<CollectedIssue> issues = List.of();
        List<CollectedPullRequest> prs = List.of();
        boolean isPartial = false;
        String partialReason = null;

        // ② Issue 수집 (부분 실패 허용)
        try {
            issues = issueCollector.collect(owner, repoName, githubLogin, accessToken);
        } catch (Exception e) {
            log.warn("GitHubMetadataService: issue collection failed for {}: {}", repoNameWithOwner, e.getMessage());
            isPartial = true;
            partialReason = "issue collection failed";
        }

        // ③ PR 수집 (부분 실패 허용)
        try {
            prs = pullRequestCollector.collect(owner, repoName, githubLogin, accessToken);
        } catch (Exception e) {
            log.warn("GitHubMetadataService: PR collection failed for {}: {}", repoNameWithOwner, e.getMessage());
            isPartial = true;
            partialReason = partialReason != null ? partialReason + ", PR collection failed" : "PR collection failed";
        }

        // ⑥⑦ 텍스트 정제는 각 Collector 내부에서 이미 적용됨
        // 토큰 예산 집행
        GitHubActivitySummary raw = new GitHubActivitySummary(
                repoNameWithOwner, issues, prs,
                new CollectionMeta(Instant.now(), 0, isPartial, partialReason)
        );

        GitHubActivitySummary enforced = tokenBudgetEnforcer.enforce(raw, commitTitles != null ? commitTitles : List.of());

        // Rate Limit 잔량 기록 (디버깅용)
        int remaining = 0;
        try {
            remaining = githubApiClient.getRateLimit(accessToken).graphqlRemaining();
        } catch (Exception ignored) {}

        return new GitHubActivitySummary(
                enforced.repoNameWithOwner(),
                enforced.createdIssues(),
                enforced.authoredPullRequests(),
                new CollectionMeta(Instant.now(), remaining, isPartial, partialReason)
        );
    }
}
