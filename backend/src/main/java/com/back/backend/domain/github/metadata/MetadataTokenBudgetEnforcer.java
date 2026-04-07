package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.metadata.dto.CollectedIssue;
import com.back.backend.domain.github.metadata.dto.CollectedPullRequest;
import com.back.backend.domain.github.metadata.dto.GitHubActivitySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 수집된 GitHub 메타데이터의 토큰 예산을 집행한다.
 * AI 프롬프트 주입 전 총 문자 수가 예산을 초과하지 않도록 우선순위 기반 슬라이싱.
 *
 * <h3>예산 기준</h3>
 * <pre>
 * tokenBudget = 1,500 tokens (application.yml)
 * charBudget  = tokenBudget × 4 (1 token ≈ 4 chars)
 * </pre>
 *
 * <h3>예산 초과 시 trim 순서 (낮은 우선순위부터)</h3>
 * <ol>
 *   <li>Issue body → title만 남김</li>
 *   <li>Issue Top 5 → Top 3 축소</li>
 *   <li>PR body → title만 남김</li>
 *   <li>CHANGES_REQUESTED review body 드롭 (reviewer + state 메타만 유지)</li>
 *   <li>PR Top 5 → Top 3 축소</li>
 * </ol>
 *
 * <p>PR title↔commit 중복 탐지: substring 포함 여부 (MVP Safe Default).
 * 중복 PR은 body를 null 처리하여 token 절약. (§10 미결정: Jaccard 고도화 검토 예정)
 */
@Component
public class MetadataTokenBudgetEnforcer {

    private static final Logger log = LoggerFactory.getLogger(MetadataTokenBudgetEnforcer.class);

    private static final int CHARS_PER_TOKEN = 4;

    private final GithubCollectionProperties properties;

    public MetadataTokenBudgetEnforcer(GithubCollectionProperties properties) {
        this.properties = properties;
    }

    /**
     * GitHubActivitySummary가 토큰 예산 내에 들어오도록 슬라이싱한다.
     *
     * @param summary        수집된 전체 메타데이터
     * @param commitTitles   기존 커밋 제목 목록 (PR title 중복 탐지용)
     * @return 예산 내로 슬라이싱된 요약 (새 인스턴스 반환)
     */
    public GitHubActivitySummary enforce(
            GitHubActivitySummary summary,
            List<String> commitTitles
    ) {
        int maxChars = properties.getMetadata().getTokenBudget() * CHARS_PER_TOKEN;

        List<CollectedIssue> issues = new ArrayList<>(summary.createdIssues());
        List<CollectedPullRequest> prs = new ArrayList<>(summary.authoredPullRequests());

        // PR title↔commit 중복: body를 null로 설정 (token 절약)
        prs = removeDuplicatePrBodies(prs, commitTitles);

        // 예산 내이면 즉시 반환
        if (estimateChars(issues, prs) <= maxChars) {
            return new GitHubActivitySummary(
                    summary.repoNameWithOwner(), issues, prs, summary.meta());
        }

        // 1단계: Issue body → title만
        issues = stripIssueBodies(issues);
        if (estimateChars(issues, prs) <= maxChars) {
            log.debug("Budget enforcer: applied stage 1 (strip issue bodies)");
            return new GitHubActivitySummary(summary.repoNameWithOwner(), issues, prs, summary.meta());
        }

        // 2단계: Issue Top 3
        issues = issues.subList(0, Math.min(3, issues.size()));
        if (estimateChars(issues, prs) <= maxChars) {
            log.debug("Budget enforcer: applied stage 2 (issue top3)");
            return new GitHubActivitySummary(summary.repoNameWithOwner(), issues, prs, summary.meta());
        }

        // 3단계: PR body → title만
        prs = stripPrBodies(prs);
        if (estimateChars(issues, prs) <= maxChars) {
            log.debug("Budget enforcer: applied stage 3 (strip PR bodies)");
            return new GitHubActivitySummary(summary.repoNameWithOwner(), issues, prs, summary.meta());
        }

        // 4단계: review body 드롭 (reviewer 메타만 유지)
        prs = dropReviewBodies(prs);
        if (estimateChars(issues, prs) <= maxChars) {
            log.debug("Budget enforcer: applied stage 4 (drop review bodies)");
            return new GitHubActivitySummary(summary.repoNameWithOwner(), issues, prs, summary.meta());
        }

        // 5단계: PR Top 3
        prs = prs.subList(0, Math.min(3, prs.size()));
        log.debug("Budget enforcer: applied stage 5 (PR top3)");
        return new GitHubActivitySummary(summary.repoNameWithOwner(), issues, prs, summary.meta());
    }

    /** PR title이 커밋 메시지에 이미 포함된 경우 body를 null 처리 (substring 중복 탐지) */
    private List<CollectedPullRequest> removeDuplicatePrBodies(
            List<CollectedPullRequest> prs, List<String> commitTitles
    ) {
        return prs.stream().map(pr -> {
            boolean duplicate = commitTitles.stream().anyMatch(ct ->
                    ct != null && pr.title() != null &&
                    (ct.toLowerCase().contains(pr.title().toLowerCase()) ||
                     pr.title().toLowerCase().contains(ct.toLowerCase()))
            );
            if (duplicate && pr.bodyExcerpt() != null) {
                return new CollectedPullRequest(
                        pr.number(), pr.title(), null,
                        pr.mergedAt(), pr.createdAt(), pr.reviews());
            }
            return pr;
        }).toList();
    }

    private List<CollectedIssue> stripIssueBodies(List<CollectedIssue> issues) {
        return issues.stream()
                .map(i -> new CollectedIssue(i.number(), i.title(), null, i.closedAt(), i.createdAt()))
                .toList();
    }

    private List<CollectedPullRequest> stripPrBodies(List<CollectedPullRequest> prs) {
        return prs.stream()
                .map(pr -> new CollectedPullRequest(
                        pr.number(), pr.title(), null,
                        pr.mergedAt(), pr.createdAt(), pr.reviews()))
                .toList();
    }

    private List<CollectedPullRequest> dropReviewBodies(List<CollectedPullRequest> prs) {
        return prs.stream()
                .map(pr -> new CollectedPullRequest(
                        pr.number(), pr.title(), pr.bodyExcerpt(),
                        pr.mergedAt(), pr.createdAt(), List.of()))
                .toList();
    }

    /** 문자 수 기반 토큰 추정 (1 token ≈ 4 chars) */
    private int estimateChars(List<CollectedIssue> issues, List<CollectedPullRequest> prs) {
        int total = 0;
        for (CollectedIssue i : issues) {
            total += i.title() != null ? i.title().length() : 0;
            total += i.bodyExcerpt() != null ? i.bodyExcerpt().length() : 0;
        }
        for (CollectedPullRequest pr : prs) {
            total += pr.title() != null ? pr.title().length() : 0;
            total += pr.bodyExcerpt() != null ? pr.bodyExcerpt().length() : 0;
            for (var rv : pr.reviews()) {
                total += rv.reviewerLogin() != null ? rv.reviewerLogin().length() : 0;
                total += rv.bodyExcerpt() != null ? rv.bodyExcerpt().length() : 0;
            }
        }
        // JSON 구조 오버헤드 추정 (키 이름 + 괄호 등)
        total += (issues.size() + prs.size()) * 50;
        return total;
    }
}
