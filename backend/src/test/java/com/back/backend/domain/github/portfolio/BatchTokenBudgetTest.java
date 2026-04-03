package com.back.backend.domain.github.portfolio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchTokenBudgetTest {

    // ─────────────────────────────────────────────────────────────────────
    // 생성자 검증
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("repoCount가 0이면 IllegalArgumentException이 발생한다")
    void constructor_throwsWhenRepoCountIsZero() {
        assertThatThrownBy(() -> new BatchTokenBudget(480_000, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("repoCount가 음수이면 IllegalArgumentException이 발생한다")
    void constructor_throwsWhenRepoCountIsNegative() {
        assertThatThrownBy(() -> new BatchTokenBudget(480_000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Repo-level Rollover
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Repo-level Rollover")
    class RepoLevelRollover {

        @Test
        @DisplayName("첫 번째 repo는 globalBudget/repoCount를 예산으로 받는다")
        void allocateForRepo_returnsBasePerRepoOnFirstCall() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 3);

            assertThat(budget.allocateForRepo()).isEqualTo(160_000);
        }

        @Test
        @DisplayName("이전 repo 잔여분이 다음 repo 예산에 합산된다")
        void commitRepoUsage_rollsOverUnusedToNextRepo() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 3); // base=160_000

            int allocated = budget.allocateForRepo();   // 160_000
            budget.commitRepoUsage(80_000);             // 80_000 남음 → 다음으로 이월

            assertThat(budget.allocateForRepo()).isEqualTo(240_000); // 160_000 + 80_000
        }

        @Test
        @DisplayName("실제 사용이 할당을 초과해도 이월분은 0이다")
        void commitRepoUsage_rolloverIsZeroWhenOverBudget() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 3); // base=160_000

            budget.allocateForRepo();
            budget.commitRepoUsage(200_000); // 초과 사용

            assertThat(budget.allocateForRepo()).isEqualTo(160_000); // 이월 없음
        }

        @Test
        @DisplayName("이월은 누적되지 않고 직전 repo 잔여분만 반영된다")
        void commitRepoUsage_rolloverIsOnlyFromPreviousRepo() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 3); // base=160_000

            // Repo A: 40_000 사용 → 120_000 이월
            budget.allocateForRepo();
            budget.commitRepoUsage(40_000);

            // Repo B: 전부 사용 (280_000) → 이월 없음
            int repoBBudget = budget.allocateForRepo(); // 160_000 + 120_000 = 280_000
            budget.commitRepoUsage(repoBBudget);

            // Repo C: 이월 없이 기본 160_000만
            assertThat(budget.allocateForRepo()).isEqualTo(160_000);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Phase-level Rollover
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Phase-level Rollover")
    class PhaseLevelRollover {

        @Test
        @DisplayName("allocatePhases는 diff 예산을 3등분한다 (나머지는 Late 흡수)")
        void allocatePhases_splitsDiffBudgetInThirds() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            int[] phases = budget.allocatePhases(300_000, 0);

            assertThat(phases[0]).isEqualTo(100_000); // Early
            assertThat(phases[1]).isEqualTo(100_000); // Mid
            assertThat(phases[2]).isEqualTo(100_000); // Late (300_000 - 200_000)
        }

        @Test
        @DisplayName("fixedUsed를 차감한 나머지를 3등분한다")
        void allocatePhases_subtractsFixedUsedFirst() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            int[] phases = budget.allocatePhases(240_000, 60_000); // diffBudget=180_000

            assertThat(phases[0]).isEqualTo(60_000);
            assertThat(phases[1]).isEqualTo(60_000);
            assertThat(phases[2]).isEqualTo(60_000);
        }

        @Test
        @DisplayName("fixedUsed가 repoBudget을 초과하면 diff 예산은 0이다")
        void allocatePhases_returnsZeroWhenFixedUsedExceedsRepoBudget() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);

            int[] phases = budget.allocatePhases(100_000, 150_000);

            assertThat(phases[0]).isEqualTo(0);
            assertThat(phases[1]).isEqualTo(0);
            assertThat(phases[2]).isEqualTo(0);
        }

        @Test
        @DisplayName("Early 잔여분이 Mid 예산에 합산된다")
        void rolloverPhase_earlyUnusedAddedToMid() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);
            int[] phases = budget.allocatePhases(300_000, 0); // [100_000, 100_000, 100_000]

            phases = budget.rolloverPhase(phases, 0, 40_000); // Early 60_000 남음

            assertThat(phases[1]).isEqualTo(160_000); // Mid: 100_000 + 60_000
            assertThat(phases[2]).isEqualTo(100_000); // Late는 아직 변화 없음
        }

        @Test
        @DisplayName("Mid 잔여분이 Late 예산에 합산된다")
        void rolloverPhase_midUnusedAddedToLate() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);
            int[] phases = budget.allocatePhases(300_000, 0); // [100_000, 100_000, 100_000]

            phases = budget.rolloverPhase(phases, 1, 30_000); // Mid 70_000 남음

            assertThat(phases[2]).isEqualTo(170_000); // Late: 100_000 + 70_000
        }

        @Test
        @DisplayName("Late(마지막) 구간에서 rolloverPhase를 호출해도 배열이 변하지 않는다")
        void rolloverPhase_doesNothingForLastPhase() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);
            int[] phases = budget.allocatePhases(300_000, 0); // [100_000, 100_000, 100_000]

            int[] result = budget.rolloverPhase(phases, 2, 50_000);

            assertThat(result[0]).isEqualTo(100_000);
            assertThat(result[1]).isEqualTo(100_000);
            assertThat(result[2]).isEqualTo(100_000);
        }

        @Test
        @DisplayName("실제 사용이 phase 예산을 초과해도 이월분은 0이다")
        void rolloverPhase_rolloverIsZeroWhenOverBudget() {
            BatchTokenBudget budget = new BatchTokenBudget(480_000, 1);
            int[] phases = budget.allocatePhases(300_000, 0); // [100_000, 100_000, 100_000]

            phases = budget.rolloverPhase(phases, 0, 150_000); // Early 초과 사용

            assertThat(phases[1]).isEqualTo(100_000); // Mid는 이월 없이 그대로
        }

        @Test
        @DisplayName("3등분 나머지는 Late 구간이 흡수한다 (diffBudget=10)")
        void allocatePhases_remainderAbsorbedByLate() {
            BatchTokenBudget budget = new BatchTokenBudget(10, 1);

            int[] phases = budget.allocatePhases(10, 0); // 10 / 3 = 3, 3, 4

            assertThat(phases[0]).isEqualTo(3);
            assertThat(phases[1]).isEqualTo(3);
            assertThat(phases[2]).isEqualTo(4);
        }
    }
}
