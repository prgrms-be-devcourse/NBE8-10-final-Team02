package com.back.backend.domain.github.metadata.dto;

import java.time.Instant;
import java.util.List;

/**
 * 수집된 GitHub 메타데이터의 최종 집계 DTO.
 * BatchPortfolioPromptBuilder의 {@code <github_activity>} 섹션으로 직렬화된다.
 *
 * <p>reviewActivities 미포함 이유: 타인 PR 리뷰는 자소서/면접 목적 범위 밖.
 *
 * @param repoNameWithOwner    "owner/repo" 형식
 * @param createdIssues        본인 작성 Issue (CLOSED + COMPLETED, Impact Score 상위 N개)
 * @param authoredPullRequests 본인 MERGED PR (CHANGES_REQUESTED 리뷰 포함, 상위 N개)
 * @param meta                 수집 메타정보 (디버깅·감사용)
 */
public record GitHubActivitySummary(
        String repoNameWithOwner,
        List<CollectedIssue> createdIssues,
        List<CollectedPullRequest> authoredPullRequests,
        CollectionMeta meta
) {

    /**
     * @param collectedAt              수집 완료 시각
     * @param rateLimitRemainingAfter  수집 후 남은 GraphQL Rate Limit
     * @param isPartial                일부 데이터만 수집된 경우 true
     * @param partialReason            isPartial=true일 때 사유
     */
    public record CollectionMeta(
            Instant collectedAt,
            int rateLimitRemainingAfter,
            boolean isPartial,
            String partialReason
    ) {}
}
