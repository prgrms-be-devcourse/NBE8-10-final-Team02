package com.back.backend.domain.github.portfolio;

/**
 * Batch AI 호출 1회에 대한 2차원 토큰 예산 관리자.
 *
 * <p><b>Spring Bean이 아님</b> — {@code BatchRepoSummaryGeneratorService}에서
 * 배치 호출 1회마다 {@code new BatchTokenBudget(globalBudget, repoCount)}로 생성한다.
 *
 * <h3>1차원: Repo 간 예산 이월 (Repo-level Rollover)</h3>
 * <pre>
 * N개 repo에 globalBudget/N 씩 균등 배분.
 * repo[i]가 덜 쓰면 남은 만큼 repo[i+1]의 예산에 더함.
 * → 작은 repo가 절약한 예산을 큰 repo가 활용.
 *
 * 예시 (globalBudget=480_000, N=3):
 *   base = 160_000
 *   Repo A: allocated=160_000  used=80_000  rollover=+80_000
 *   Repo B: allocated=240_000  used=240_000 rollover=0
 *   Repo C: allocated=160_000  used=100_000 rollover=0 (마지막)
 * </pre>
 *
 * <h3>2차원: Repo 내 구간 간 예산 이월 (Phase-level Rollover)</h3>
 * <pre>
 * 단일 repo의 diff 예산을 Early/Mid/Late 3등분.
 * Early가 덜 쓰면 → Mid에 이월, Mid가 덜 쓰면 → Late에 이월.
 * → 초기 구간이 빈약해도 후기 구간(트러블슈팅)이 더 많은 공간을 확보.
 *
 * 예시 (repoBudget=240_000, fixedUsed=20_000):
 *   diffBudget  = 220_000 → 3등분 ≈ 73_333 each
 *   Early: budget=73_333  used=40_000  rollover=+33_333
 *   Mid:   budget=106_666 used=100_000 rollover=+6_666
 *   Late:  budget=80_000  used=80_000  rollover=0
 * </pre>
 */
public class BatchTokenBudget {

    // ─── 1차원(Repo-level) 상태 ───────────────────────────────
    /** 각 repo에 기본 배분되는 chars (= globalBudget / repoCount) */
    private final int basePerRepo;

    /**
     * 이전 repo에서 남아 다음 repo로 이월되는 잔여 예산.
     * 첫 번째 repo에는 0으로 시작한다.
     */
    private int repoLevelCarryover;

    // ─────────────────────────────────────────────────────────

    /**
     * @param globalBudgetChars Batch 호출 1회 전체 char 예산
     * @param repoCount         배치에 포함된 repo 수 (0이면 예외)
     */
    public BatchTokenBudget(int globalBudgetChars, int repoCount) {
        if (repoCount <= 0) {
            throw new IllegalArgumentException("repoCount는 1 이상이어야 합니다: " + repoCount);
        }
        this.basePerRepo = globalBudgetChars / repoCount;
        this.repoLevelCarryover = 0;
    }

    // ─────────────────────────────────────────────────────────
    // Repo-level API
    // ─────────────────────────────────────────────────────────

    /**
     * 현재 repo가 사용할 수 있는 char 예산을 반환한다.
     *
     * <p>이전 repo에서 이월된 잔여가 있으면 합산된다.
     *
     * @return 이 repo의 가용 char 예산 (= basePerRepo + 이전 rollover)
     */
    public int allocateForRepo() {
        return basePerRepo + repoLevelCarryover;
    }

    /**
     * 현재 repo의 실제 사용량을 기록하고, 잔여를 다음 repo로 이월한다.
     *
     * <p>반드시 {@link #allocateForRepo()} 호출 후 실제 사용이 끝난 뒤에 호출한다.
     *
     * @param actualUsed 현재 repo에서 실제로 사용한 char 수
     */
    public void commitRepoUsage(int actualUsed) {
        int allocated = basePerRepo + repoLevelCarryover;
        // 사용량이 할당보다 크면 이월 없음 (음수 방지)
        int unused = Math.max(0, allocated - actualUsed);
        // 다음 repo의 repoLevelCarryover는 이번 repo의 잔여분만 (누적하지 않음)
        this.repoLevelCarryover = unused;
    }

    // ─────────────────────────────────────────────────────────
    // Phase-level API (Repo 내부 Early / Mid / Late 구간)
    // ─────────────────────────────────────────────────────────

    /**
     * 단일 Repo 내부에서 3개 diff 구간(Early/Mid/Late)의 char 예산을 계산한다.
     *
     * <p>구간 간 이월 규칙:
     * <ul>
     *   <li>Early 잔여 → Mid에 추가</li>
     *   <li>Mid 잔여 → Late에 추가</li>
     * </ul>
     *
     * @param repoBudget 이 repo의 전체 char 예산 ({@link #allocateForRepo()} 반환값)
     * @param fixedUsed  overview + code_structure 등 고정 섹션이 사용한 chars
     *                   (diff 이전에 소비되므로 먼저 차감)
     * @return Early/Mid/Late 3구간 각각의 char 예산 배열 (인덱스: 0=Early, 1=Mid, 2=Late)
     */
    public int[] allocatePhases(int repoBudget, int fixedUsed) {
        // diff에 쓸 수 있는 총 예산 (고정 섹션 소비분 차감, 음수 방지)
        int diffBudget = Math.max(0, repoBudget - fixedUsed);

        // 3등분 기본 배분 (나머지는 마지막 구간이 흡수)
        int basePerPhase = diffBudget / 3;
        int lateBase = diffBudget - 2 * basePerPhase; // 나머지 흡수

        // 구간 예산을 담을 배열 (초기값: 균등 배분)
        int[] phaseBudgets = { basePerPhase, basePerPhase, lateBase };
        return phaseBudgets;
    }

    /**
     * 이전 구간의 잔여 예산을 다음 구간으로 이월하여 갱신된 예산 배열을 반환한다.
     *
     * <p>호출 예시 (빌더에서 each phase를 조립한 뒤):
     * <pre>
     *   int[] budgets = budget.allocatePhases(repoBudget, fixedUsed);
     *   // Early 조립 완료 후:
     *   budgets = budget.rolloverPhase(budgets, 0, earlyActualUsed);
     *   // Mid 조립 완료 후:
     *   budgets = budget.rolloverPhase(budgets, 1, midActualUsed);
     *   // Late는 rollover 없음 (마지막 구간)
     * </pre>
     *
     * @param phaseBudgets 현재 구간 예산 배열 (3원소, 원본 수정 후 반환)
     * @param completedPhaseIndex 방금 완료된 구간 인덱스 (0=Early, 1=Mid)
     * @param actualUsed 방금 완료된 구간에서 실제 사용한 chars
     * @return 다음 구간 예산이 갱신된 배열 (같은 배열 참조)
     */
    public int[] rolloverPhase(int[] phaseBudgets, int completedPhaseIndex, int actualUsed) {
        int nextPhaseIndex = completedPhaseIndex + 1;
        if (nextPhaseIndex >= phaseBudgets.length) {
            return phaseBudgets; // 마지막 구간이면 이월 없음
        }
        int unused = Math.max(0, phaseBudgets[completedPhaseIndex] - actualUsed);
        phaseBudgets[nextPhaseIndex] += unused; // 잔여를 다음 구간에 추가
        return phaseBudgets;
    }
}
