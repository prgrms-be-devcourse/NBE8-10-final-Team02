'use client';

import { useEffect, useRef, useState } from 'react';
import { useBatchAnalysis } from '@/context/BatchAnalysisContext';
import type { RepoSyncStatus } from '@/types/github';

// ── 헬퍼 ──────────────────────────────────────────────────────────────

function countByStatus(
  repoIds: number[],
  statuses: Record<number, RepoSyncStatus | null>
) {
  let completed = 0;
  let failed = 0;
  let inProgress = 0;
  repoIds.forEach((id) => {
    const s = statuses[id];
    if (s?.status === 'COMPLETED' || s?.status === 'SKIPPED') completed++;
    else if (s?.status === 'FAILED') failed++;
    else inProgress++;
  });
  return { completed, failed, inProgress, total: repoIds.length };
}

const RETRYABLE_PREFIX = '[RETRY]';
const isRetryable = (error?: string | null) => error?.startsWith(RETRYABLE_PREFIX) ?? false;
const displayError = (error?: string | null) =>
  error?.startsWith(RETRYABLE_PREFIX) ? error.slice(RETRYABLE_PREFIX.length).trim() : (error ?? '');

const STEP_LABEL: Record<string, string> = {
  significance_check: '변경 감지 중',
  clone: '저장소 복제 중',
  analysis: '코드 분석 중',
  ai_pending: 'AI 분석 대기 중',
  summary: 'AI 요약 생성 중',
};

/** 배치 내 진행 중인 repo들의 estimatedEndAt 중 가장 늦은 값을 반환 */
function resolveEstimatedEndAt(
  repoIds: number[],
  statuses: Record<number, RepoSyncStatus | null>
): Date | null {
  let latest: Date | null = null;
  for (const id of repoIds) {
    const s = statuses[id];
    if (!s || s.status === 'COMPLETED' || s.status === 'FAILED' || s.status === 'SKIPPED') continue;
    if (!s.estimatedEndAt) continue;
    const d = new Date(s.estimatedEndAt);
    if (!latest || d > latest) latest = d;
  }
  return latest;
}

/** 남은 초를 "N분 N초" 형태로 포맷 */
function formatRemaining(seconds: number): string {
  if (seconds <= 0) return '곧 완료';
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  if (m === 0) return `${s}초 남음`;
  return `${m}분 ${s}초 남음`;
}

// ── 카운트다운 훅 ──────────────────────────────────────────────────────

function useCountdown(estimatedEndAt: Date | null): string | null {
  const [remaining, setRemaining] = useState<number | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!estimatedEndAt) {
      setRemaining(null);
      return;
    }

    const tick = () => {
      const diff = Math.round((estimatedEndAt.getTime() - Date.now()) / 1000);
      setRemaining(Math.max(0, diff));
    };

    tick();
    timerRef.current = setInterval(tick, 1000);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [estimatedEndAt?.getTime()]); // eslint-disable-line react-hooks/exhaustive-deps

  if (remaining === null) return null;
  return formatRemaining(remaining);
}

// ── 컴포넌트 ─────────────────────────────────────────────────────────

export default function BatchProgressWidget() {
  const { activeBatch, handleCancelBatch, handleRetryFailed, dismissWidget } = useBatchAnalysis();
  const [cancelling, setCancelling] = useState(false);
  const [expanded, setExpanded] = useState(false);

  const estimatedEndAt = activeBatch
    ? resolveEstimatedEndAt(activeBatch.repoIds, activeBatch.statuses)
    : null;
  const countdownLabel = useCountdown(estimatedEndAt);

  if (!activeBatch) return null;

  const { repoIds, statuses } = activeBatch;
  const counts = countByStatus(repoIds, statuses);
  const failedIds = repoIds.filter((id) => statuses[id]?.status === 'FAILED');
  const allDone = counts.inProgress === 0;
  const allSuccess = allDone && counts.failed === 0;
  const hasFailure = counts.failed > 0;

  async function onCancel() {
    setCancelling(true);
    try {
      await handleCancelBatch();
    } finally {
      setCancelling(false);
    }
  }

  // ── 전체 성공 완료 상태 ──
  if (allSuccess) {
    return (
      <div className="fixed bottom-4 right-4 z-50 flex items-center gap-3 rounded-2xl border border-green-200 bg-white px-4 py-3 shadow-lg">
        <span className="text-sm font-medium text-green-700">✓ 포트폴리오 분석 완료</span>
        <button
          onClick={dismissWidget}
          className="text-xs text-zinc-400 hover:text-zinc-600 cursor-pointer"
        >
          닫기
        </button>
      </div>
    );
  }

  // ── 일부 실패 완료 상태 ──
  if (allDone && hasFailure) {
    const allFailuresRetryable = failedIds.every((id) => isRetryable(statuses[id]?.error));
    return (
      <div className="fixed bottom-4 right-4 z-50 w-72 rounded-2xl border border-amber-200 bg-white shadow-lg">
        <div className="flex items-center justify-between px-4 py-3">
          <button
            onClick={() => setExpanded((v) => !v)}
            className="flex flex-1 items-center gap-2 text-left cursor-pointer"
          >
            <span className="text-base">⚠️</span>
            <div className="flex flex-col">
              <span className="text-sm font-medium text-amber-700">
                분석 완료 ({counts.completed}/{counts.total}), {counts.failed}개 실패
              </span>
              {allFailuresRetryable && (
                <span className="text-xs text-amber-600">재시도하면 성공할 수 있습니다</span>
              )}
            </div>
          </button>
          <button
            onClick={() => setExpanded((v) => !v)}
            className="text-xs text-zinc-400 hover:text-zinc-600 cursor-pointer shrink-0 ml-2"
          >
            {expanded ? '▲' : '▼'}
          </button>
        </div>

        {/* 진행 바 (완료 비율) */}
        <div className="px-4 pb-2">
          <div className="h-1 w-full rounded-full bg-zinc-100">
            <div
              className="h-1 rounded-full bg-amber-400 transition-all duration-500"
              style={{ width: `${(counts.completed / counts.total) * 100}%` }}
            />
          </div>
        </div>

        {/* 확장 패널 */}
        {expanded && (
          <ul className="mx-4 mb-2 mt-1 flex flex-col gap-1.5 max-h-48 overflow-y-auto">
            {repoIds.map((id) => {
              const s = statuses[id];
              const isDone = s?.status === 'COMPLETED' || s?.status === 'SKIPPED';
              const isFailed = s?.status === 'FAILED';
              const retryable = isFailed && isRetryable(s?.error);
              const name = activeBatch.repoNames?.[id] ?? `repo #${id}`;
              return (
                <li key={id} className="flex items-start gap-2 text-xs">
                  <span className="mt-0.5 shrink-0">
                    {isDone && <span className="text-green-600">✓</span>}
                    {retryable && <span className="text-amber-500">↺</span>}
                    {isFailed && !retryable && <span className="text-red-500">✕</span>}
                  </span>
                  <span className={`min-w-0 ${retryable ? 'text-amber-700' : isFailed ? 'text-red-700' : 'text-zinc-700'}`}>
                    <span className="truncate block">{name}</span>
                    {isFailed && s?.error && (
                      <span className={retryable ? 'text-amber-500' : 'text-red-500'}>
                        {displayError(s.error)}
                      </span>
                    )}
                    {s?.status === 'SKIPPED' && s?.skipReason && (
                      <span className="text-zinc-400">{s.skipReason}</span>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        )}

        <div className="flex gap-2 border-t border-zinc-100 px-4 py-2">
          <button
            onClick={handleRetryFailed}
            className="flex-1 rounded border border-indigo-300 bg-indigo-50 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-100 cursor-pointer"
          >
            {allFailuresRetryable ? `↺ 재시도 (${failedIds.length}개)` : `실패 재시도 (${failedIds.length}개)`}
          </button>
          <button
            onClick={dismissWidget}
            className="rounded border border-zinc-200 px-3 py-1.5 text-xs text-zinc-500 hover:bg-zinc-50 cursor-pointer"
          >
            닫기
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed bottom-4 right-4 z-50 w-72 rounded-2xl border border-zinc-200 bg-white shadow-lg">
      {/* 헤더 */}
      <div className="flex items-center justify-between px-4 py-3">
        <button
          onClick={() => setExpanded((v) => !v)}
          className="flex flex-1 items-center gap-2 text-left cursor-pointer"
        >
          <span className="animate-spin text-base">⏳</span>
          <span className="text-sm font-medium text-zinc-800">
            포트폴리오 분석 중 ({counts.completed}/{counts.total} 완료)
          </span>
        </button>
        <div className="flex items-center gap-2 shrink-0 ml-2">
          {hasFailure && (
            <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">
              {counts.failed}개 실패
            </span>
          )}
          <button
            onClick={() => setExpanded((v) => !v)}
            className="text-xs text-zinc-400 hover:text-zinc-600 cursor-pointer"
          >
            {expanded ? '▲' : '▼'}
          </button>
        </div>
      </div>

      {/* 진행 바 + 카운트다운 */}
      <div className="px-4 pb-2">
        <div className="h-1 w-full rounded-full bg-zinc-100">
          <div
            className="h-1 rounded-full bg-indigo-500 transition-all duration-500"
            style={{ width: `${(counts.completed / counts.total) * 100}%` }}
          />
        </div>
        {countdownLabel && (
          <p className="mt-1 text-right text-xs text-zinc-400">{countdownLabel}</p>
        )}
      </div>

      {/* 확장 패널 — repo 목록 */}
      {expanded && (
        <ul className="mx-4 mb-2 mt-1 flex flex-col gap-1.5 max-h-48 overflow-y-auto">
          {repoIds.map((id) => {
            const s = statuses[id];
            const isDone = s?.status === 'COMPLETED' || s?.status === 'SKIPPED';
            const isFailed = s?.status === 'FAILED';
            const retryable = isFailed && isRetryable(s?.error);
            const isActive = !isDone && !isFailed;
            const name = activeBatch.repoNames?.[id] ?? `repo #${id}`;
            const stepLabel = isActive
              ? (s?.step ? STEP_LABEL[s.step] ?? s.step : '준비 중')
              : null;
            return (
              <li key={id} className="flex items-start gap-2 text-xs">
                <span className="mt-0.5 shrink-0">
                  {isDone && <span className="text-green-600">✓</span>}
                  {retryable && <span className="text-amber-500">↺</span>}
                  {isFailed && !retryable && <span className="text-red-500">✕</span>}
                  {isActive && <span className="animate-spin inline-block text-indigo-400">⟳</span>}
                </span>
                <span className={`min-w-0 ${retryable ? 'text-amber-700' : isFailed ? 'text-red-700' : 'text-zinc-700'}`}>
                  <span className="truncate block">{name}</span>
                  {stepLabel && (
                    <span className="text-indigo-400 animate-pulse">{stepLabel}...</span>
                  )}
                  {isFailed && s?.error && (
                    <span className={retryable ? 'text-amber-500' : 'text-red-500'}>
                      {displayError(s.error)}
                    </span>
                  )}
                  {s?.status === 'SKIPPED' && s?.skipReason && (
                    <span className="text-zinc-400">{s.skipReason}</span>
                  )}
                </span>
              </li>
            );
          })}
        </ul>
      )}

      {/* 액션 버튼 */}
      <div className="flex gap-2 border-t border-zinc-100 px-4 py-2">
        {hasFailure && allDone && (
          <button
            onClick={handleRetryFailed}
            className="flex-1 rounded border border-indigo-300 bg-indigo-50 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-100 cursor-pointer"
          >
            실패 repo 재시도 ({failedIds.length}개)
          </button>
        )}
        {!allDone && (
          <button
            onClick={onCancel}
            disabled={cancelling}
            className="flex-1 rounded border border-red-200 bg-red-50 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {cancelling ? '중지 중...' : '분석 중지'}
          </button>
        )}
        {hasFailure && !allDone && (
          <button
            onClick={dismissWidget}
            className="rounded border border-zinc-200 px-3 py-1.5 text-xs text-zinc-500 hover:bg-zinc-50 cursor-pointer"
          >
            닫기
          </button>
        )}
      </div>
    </div>
  );
}
