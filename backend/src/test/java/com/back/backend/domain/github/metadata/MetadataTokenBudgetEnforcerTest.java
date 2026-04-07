package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.metadata.dto.CollectedIssue;
import com.back.backend.domain.github.metadata.dto.CollectedPullRequest;
import com.back.backend.domain.github.metadata.dto.CollectedReview;
import com.back.backend.domain.github.metadata.dto.GitHubActivitySummary;
import com.back.backend.domain.github.metadata.dto.GitHubActivitySummary.CollectionMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataTokenBudgetEnforcerTest {

    private MetadataTokenBudgetEnforcer enforcer;
    private GithubCollectionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GithubCollectionProperties();
        // 기본 tokenBudget=1500 → maxChars=6000
        enforcer = new MetadataTokenBudgetEnforcer(properties);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 예산 내 통과 (pass-through)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("예산 내 통과")
    class WithinBudget {

        @Test
        @DisplayName("총 문자 수가 예산 이하이면 원본 데이터 그대로 반환")
        void enforce_returnsUnchanged_whenWithinBudget() {
            List<CollectedIssue> issues = List.of(
                    issue(1, "짧은 제목", "짧은 본문")
            );
            List<CollectedPullRequest> prs = List.of(
                    pr(1, "PR 제목", "PR 본문", List.of())
            );

            GitHubActivitySummary result = enforcer.enforce(summary(issues, prs), List.of());

            assertThat(result.createdIssues()).hasSize(1);
            assertThat(result.authoredPullRequests()).hasSize(1);
            assertThat(result.createdIssues().get(0).bodyExcerpt()).isEqualTo("짧은 본문");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PR title↔commit 중복 탐지
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PR title-commit 중복 탐지")
    class DuplicatePrBody {

        @Test
        @DisplayName("PR title이 커밋 메시지에 포함되면 PR body를 null 처리")
        void enforce_nullifiesPrBody_whenTitleDuplicatesCommit() {
            List<CollectedPullRequest> prs = List.of(
                    pr(1, "feat: add login", "상세 설명", List.of())
            );
            List<String> commitTitles = List.of("feat: add login feature");

            GitHubActivitySummary result = enforcer.enforce(summary(List.of(), prs), commitTitles);

            assertThat(result.authoredPullRequests().get(0).bodyExcerpt()).isNull();
        }

        @Test
        @DisplayName("PR title이 커밋 메시지와 무관하면 body 유지")
        void enforce_keepsPrBody_whenNoTitleDuplicate() {
            List<CollectedPullRequest> prs = List.of(
                    pr(1, "feat: payment gateway", "결제 모듈 상세 설명", List.of())
            );
            List<String> commitTitles = List.of("chore: update dependencies");

            GitHubActivitySummary result = enforcer.enforce(summary(List.of(), prs), commitTitles);

            assertThat(result.authoredPullRequests().get(0).bodyExcerpt()).isEqualTo("결제 모듈 상세 설명");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5단계 trim 순서
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("5단계 trim 순서")
    class TrimStages {

        @Test
        @DisplayName("Stage 1: 예산 초과 시 Issue body를 null 처리")
        void enforce_stage1_stripsIssueBodies() {
            // tokenBudget을 매우 작게 설정하여 예산 초과 유발
            properties.getMetadata().setTokenBudget(1);  // maxChars=4
            List<CollectedIssue> issues = List.of(
                    issue(1, "제목", "매우 긴 본문 텍스트가 포함되어 있어서 예산을 넘습니다")
            );
            List<CollectedPullRequest> prs = List.of();

            GitHubActivitySummary result = enforcer.enforce(summary(issues, prs), List.of());

            assertThat(result.createdIssues().get(0).bodyExcerpt()).isNull();
        }

        @Test
        @DisplayName("Stage 2: Issue body 제거 후에도 초과 시 Issue Top 3으로 축소")
        void enforce_stage2_limitsIssuesToTop3() {
            properties.getMetadata().setTokenBudget(1);  // maxChars=4
            List<CollectedIssue> issues = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                issues.add(issue(i, "이슈제목" + i, null));
            }

            GitHubActivitySummary result = enforcer.enforce(summary(issues, List.of()), List.of());

            assertThat(result.createdIssues().size()).isLessThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Stage 3: Stage 2 이후에도 초과 시 PR body를 null 처리")
        void enforce_stage3_stripsPrBodies() {
            properties.getMetadata().setTokenBudget(1);  // maxChars=4
            List<CollectedIssue> issues = List.of(issue(1, "i", null));
            List<CollectedPullRequest> prs = List.of(
                    pr(1, "PR1", "매우 긴 PR 본문 텍스트가 예산을 초과합니다", List.of())
            );

            GitHubActivitySummary result = enforcer.enforce(summary(issues, prs), List.of());

            assertThat(result.authoredPullRequests().get(0).bodyExcerpt()).isNull();
        }

        @Test
        @DisplayName("Stage 4: PR body 제거 후에도 초과 시 review body 드롭")
        void enforce_stage4_dropsReviewBodies() {
            properties.getMetadata().setTokenBudget(1);  // maxChars=4
            List<CollectedReview> reviews = List.of(
                    new CollectedReview("reviewer1", "긴 리뷰 본문 텍스트 포함", Instant.now())
            );
            List<CollectedPullRequest> prs = List.of(
                    pr(1, "P", null, reviews)
            );

            GitHubActivitySummary result = enforcer.enforce(summary(List.of(), prs), List.of());

            // reviews가 비어있거나 body가 null인지 확인
            List<CollectedReview> resultReviews = result.authoredPullRequests().get(0).reviews();
            assertThat(resultReviews).isEmpty();
        }

        @Test
        @DisplayName("Stage 5: Stage 4 이후에도 초과 시 PR Top 3으로 축소")
        void enforce_stage5_limitsPrsToTop3() {
            properties.getMetadata().setTokenBudget(1);  // maxChars=4
            List<CollectedPullRequest> prs = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                prs.add(pr(i, "P" + i, null, List.of()));
            }

            GitHubActivitySummary result = enforcer.enforce(summary(List.of(), prs), List.of());

            assertThat(result.authoredPullRequests().size()).isLessThanOrEqualTo(3);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────

    private GitHubActivitySummary summary(List<CollectedIssue> issues, List<CollectedPullRequest> prs) {
        return new GitHubActivitySummary(
                "owner/repo", issues, prs,
                new CollectionMeta(Instant.now(), 5000, false, null));
    }

    private CollectedIssue issue(int number, String title, String body) {
        return new CollectedIssue(number, title, body, Instant.now(), Instant.now());
    }

    private CollectedPullRequest pr(int number, String title, String body, List<CollectedReview> reviews) {
        return new CollectedPullRequest(number, title, body, Instant.now(), Instant.now(), reviews);
    }
}
