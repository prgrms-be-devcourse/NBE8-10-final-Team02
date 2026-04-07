package com.back.backend.domain.github.metadata;

import com.back.backend.domain.github.metadata.dto.PrPhase1Meta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactScoreCalculatorTest {

    private ImpactScoreCalculator calculator;

    // 테스트 기준 시각 — 고정하여 recencyWeight를 결정론적으로 만든다
    private static final Instant NOW = Instant.parse("2026-04-07T00:00:00Z");
    private static final Clock   FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        GithubCollectionProperties properties = new GithubCollectionProperties();
        calculator = new ImpactScoreCalculator(properties, FIXED_CLOCK);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 하드 제외 (Hard Exclude)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("하드 제외")
    class HardExclude {

        @Test
        @DisplayName("authorTypename=Bot이면 0.0 반환")
        void score_returnsZero_whenAuthorTypenameIsBot() {
            PrPhase1Meta pr = pr().authorLogin("some-bot").authorTypename("Bot").build();
            assertThat(calculator.score(pr)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("알려진 봇 로그인(dependabot[bot])이면 0.0 반환")
        void score_returnsZero_whenAuthorIsKnownBot() {
            PrPhase1Meta pr = pr().authorLogin("dependabot[bot]").authorTypename("User").build();
            assertThat(calculator.score(pr)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("[bot] 접미사 로그인이면 0.0 반환")
        void score_returnsZero_whenAuthorLoginEndsWithBotSuffix() {
            PrPhase1Meta pr = pr().authorLogin("my-custom[bot]").authorTypename("User").build();
            assertThat(calculator.score(pr)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("addDel>5000 && review=0이면 자동생성 의심으로 0.0 반환")
        void score_returnsZero_whenHugeAddDelAndNoReview() {
            PrPhase1Meta pr = pr().additions(4000).deletions(2000).reviewTotalCount(0).build();
            assertThat(calculator.score(pr)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("addDel>5000이지만 review>0이면 0.0이 아님 (자동생성 아님)")
        void score_notZero_whenHugeAddDelButHasReview() {
            PrPhase1Meta pr = pr().additions(4000).deletions(2000).reviewTotalCount(2).build();
            assertThat(calculator.score(pr)).isGreaterThan(0.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 소프트 패널티 (Soft Penalty)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("소프트 패널티")
    class SoftPenalty {

        @Test
        @DisplayName("addDel>1000 && bodyLen<50 && review=0이면 패널티 0.1 적용")
        void score_appliesSoftPenalty_whenLargePrWithNoBodyAndNoReview() {
            // 패널티 없는 기준 PR: addDel=800 (<=1000)
            PrPhase1Meta normalPr = pr().additions(800).deletions(0).reviewTotalCount(0)
                    .bodyText("").build();

            // 패널티 대상 PR: addDel=1500 (>1000), bodyLen=0, review=0
            PrPhase1Meta penalizedPr = pr().additions(1500).deletions(0).reviewTotalCount(0)
                    .bodyText("").build();

            double normalScore = calculator.score(normalPr);
            double penalizedScore = calculator.score(penalizedPr);

            // 패널티(0.1)가 적용된 PR 점수가 현저히 낮아야 한다
            assertThat(penalizedScore).isLessThan(normalScore * 0.5);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 점수 순위 (Ranking)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("점수 순위")
    class Ranking {

        @Test
        @DisplayName("feat: prefix PR이 chore: prefix PR보다 높은 점수")
        void score_featPrRanksHigherThanChorePr() {
            PrPhase1Meta feat  = pr().title("feat: add concurrency control").build();
            PrPhase1Meta chore = pr().title("chore: update README docs").build();

            assertThat(calculator.score(feat)).isGreaterThan(calculator.score(chore));
        }

        @Test
        @DisplayName("기술 키워드 포함 PR이 일반 PR보다 높은 점수")
        void score_keywordPrRanksHigher() {
            PrPhase1Meta withKeyword = pr().title("feat: 동시성 제어 개선").build();
            PrPhase1Meta without    = pr().title("feat: UI 버튼 색상 변경").build();

            assertThat(calculator.score(withKeyword)).isGreaterThan(calculator.score(without));
        }

        @Test
        @DisplayName("최신 PR이 오래된 PR보다 높은 점수 (recency 가중치)")
        void score_recentPrRanksHigherThanOldPr() {
            PrPhase1Meta recent = pr().mergedAt(Instant.now().minus(10, ChronoUnit.DAYS)).build();
            PrPhase1Meta old    = pr().mergedAt(Instant.now().minus(700, ChronoUnit.DAYS)).build();

            assertThat(calculator.score(recent)).isGreaterThan(calculator.score(old));
        }

        @Test
        @DisplayName("리뷰 수가 많은 PR이 없는 PR보다 높은 점수")
        void score_prWithReviewsRanksHigher() {
            PrPhase1Meta reviewed    = pr().reviewTotalCount(3).build();
            PrPhase1Meta notReviewed = pr().reviewTotalCount(0).build();

            assertThat(calculator.score(reviewed)).isGreaterThan(calculator.score(notReviewed));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 라벨 커버리지 (Label Coverage)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("라벨 커버리지")
    class LabelCoverage {

        @Test
        @DisplayName("repoLabelCoverage < 0.5이면 라벨 보너스 무시")
        void score_ignoreLabelBonus_whenCoverageIsLow() {
            PrPhase1Meta withLabel   = pr().labels(List.of("feature")).repoLabelCoverage(0.3).build();
            PrPhase1Meta withoutLabel = pr().labels(List.of()).repoLabelCoverage(0.3).build();

            assertThat(calculator.score(withLabel)).isEqualTo(calculator.score(withoutLabel));
        }

        @Test
        @DisplayName("repoLabelCoverage >= 0.5이면 feature 라벨 보너스 적용")
        void score_applyLabelBonus_whenCoverageIsHigh() {
            PrPhase1Meta withLabel   = pr().labels(List.of("feature")).repoLabelCoverage(0.8).build();
            PrPhase1Meta withoutLabel = pr().labels(List.of()).repoLabelCoverage(0.8).build();

            assertThat(calculator.score(withLabel)).isGreaterThan(calculator.score(withoutLabel));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────

    private static Builder pr() {
        return new Builder();
    }

    /** 테스트용 빌더 — 기본값을 갖는 "정상" PR 생성 */
    static class Builder {
        private String id = "id1";
        private int number = 1;
        private String title = "feat: add feature";
        private String bodyText = "상세 설명이 포함된 PR 본문";
        private Instant mergedAt = NOW.minus(30, ChronoUnit.DAYS);
        private Instant createdAt = NOW;
        private int additions = 200;
        private int deletions = 50;
        private int totalCommentsCount = 2;
        private String authorLogin = "user";
        private String authorTypename = "User";
        private List<String> labels = List.of();
        private int reviewTotalCount = 1;
        private double repoLabelCoverage = 0.3;

        Builder title(String v)               { title = v; return this; }
        Builder bodyText(String v)            { bodyText = v; return this; }
        Builder mergedAt(Instant v)           { mergedAt = v; return this; }
        Builder additions(int v)              { additions = v; return this; }
        Builder deletions(int v)              { deletions = v; return this; }
        Builder reviewTotalCount(int v)       { reviewTotalCount = v; return this; }
        Builder authorLogin(String v)         { authorLogin = v; return this; }
        Builder authorTypename(String v)      { authorTypename = v; return this; }
        Builder labels(List<String> v)        { labels = v; return this; }
        Builder repoLabelCoverage(double v)   { repoLabelCoverage = v; return this; }

        PrPhase1Meta build() {
            return new PrPhase1Meta(
                    id, number, title, bodyText, mergedAt, createdAt,
                    additions, deletions, totalCommentsCount,
                    authorLogin, authorTypename, labels, reviewTotalCount, repoLabelCoverage);
        }
    }
}
