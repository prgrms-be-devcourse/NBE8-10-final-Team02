'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getSessionResult, InterviewApiError } from '@/api/interview';
import InterviewPendingResultPanel from '@/components/InterviewPendingResultPanel';
import InterviewResultReport from '@/components/InterviewResultReport';
import {
  PENDING_RESULT_AUTO_RECHECK_INTERVAL_MS,
  PENDING_RESULT_AUTO_RECHECK_MAX_ATTEMPTS,
} from '@/lib/interview-status-ui';
import type { InterviewResult } from '@/types/interview';

export default function InterviewSessionResultPage() {
  const params = useParams();
  const sessionId = Number(params.sessionId);

  const [result, setResult] = useState<InterviewResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingMessage, setPendingMessage] = useState<string | null>(null);
  const [refreshingResult, setRefreshingResult] = useState(false);
  const [autoRefreshAttempt, setAutoRefreshAttempt] = useState(0);
  const [autoRefreshActive, setAutoRefreshActive] = useState(false);
  const autoRefreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestIdRef = useRef(0);

  const clearAutoRefreshTimer = useCallback(() => {
    if (autoRefreshTimerRef.current) {
      clearTimeout(autoRefreshTimerRef.current);
      autoRefreshTimerRef.current = null;
    }
  }, []);

  const resetAutoRefreshState = useCallback(() => {
    clearAutoRefreshTimer();
    setAutoRefreshAttempt(0);
    setAutoRefreshActive(false);
  }, [clearAutoRefreshTimer]);

  const loadResult = useCallback(async (options?: {
    showPageLoading?: boolean;
    reason?: 'initial' | 'auto' | 'manual';
    autoAttempt?: number;
  }) => {
    if (!Number.isFinite(sessionId)) {
      setError('올바른 결과 경로가 아닙니다.');
      setLoading(false);
      return;
    }

    const showPageLoading = options?.showPageLoading ?? true;
    const reason = options?.reason ?? (showPageLoading ? 'initial' : 'manual');
    const requestId = ++requestIdRef.current;
    const scheduleAutoRefresh = (attempt: number) => {
      clearAutoRefreshTimer();
      setAutoRefreshAttempt(attempt);
      setAutoRefreshActive(true);
      autoRefreshTimerRef.current = setTimeout(() => {
        void loadResult({
          showPageLoading: false,
          reason: 'auto',
          autoAttempt: attempt,
        });
      }, PENDING_RESULT_AUTO_RECHECK_INTERVAL_MS);
    };

    if (showPageLoading) {
      setLoading(true);
    } else if (reason === 'manual') {
      setRefreshingResult(true);
    }
    setError(null);
    if (showPageLoading) {
      setPendingMessage(null);
    }

    try {
      const data = await getSessionResult(sessionId);
      if (requestId !== requestIdRef.current) {
        return;
      }
      resetAutoRefreshState();
      setResult(data);
      setPendingMessage(null);
    } catch (err) {
      if (requestId !== requestIdRef.current) {
        return;
      }
      setResult(null);

      if (err instanceof InterviewApiError && err.code === 'INTERVIEW_RESULT_INCOMPLETE') {
        setPendingMessage(err.message);
        if (reason === 'initial') {
          scheduleAutoRefresh(1);
          return;
        }

        if (reason === 'auto') {
          if ((options?.autoAttempt ?? 0) < PENDING_RESULT_AUTO_RECHECK_MAX_ATTEMPTS) {
            scheduleAutoRefresh((options?.autoAttempt ?? 0) + 1);
          } else {
            clearAutoRefreshTimer();
            setAutoRefreshActive(false);
          }
          return;
        }

        clearAutoRefreshTimer();
        setAutoRefreshActive(false);
      } else {
        resetAutoRefreshState();
        setError(err instanceof Error ? err.message : '면접 결과를 불러오지 못했습니다.');
      }
    } finally {
      if (requestId !== requestIdRef.current) {
        return;
      }
      if (showPageLoading) {
        setLoading(false);
      } else if (reason === 'manual') {
        setRefreshingResult(false);
      }
    }
  }, [
    clearAutoRefreshTimer,
    resetAutoRefreshState,
    sessionId,
  ]);

  useEffect(() => {
    void loadResult();
  }, [loadResult]);

  useEffect(() => () => {
    clearAutoRefreshTimer();
  }, [clearAutoRefreshTimer]);

  const handleManualRefresh = useCallback(() => {
    clearAutoRefreshTimer();
    setAutoRefreshActive(false);
    void loadResult({ showPageLoading: false, reason: 'manual' });
  }, [clearAutoRefreshTimer, loadResult]);

  if (loading) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-12">
        <p className="text-sm text-zinc-400">면접 결과를 불러오는 중...</p>
      </main>
    );
  }

  if (error) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-12">
        <div className="rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-700">
          {error}
        </div>
        <div className="mt-4 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={() => void loadResult()}
            className="rounded-full bg-zinc-900 px-4 py-2 text-sm font-medium text-white"
          >
            다시 보기
          </button>
          <Link
            href={`/interview/sessions/${sessionId}`}
            className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700"
          >
            세션으로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

  if (pendingMessage) {
    return (
      <main className="mx-auto max-w-4xl px-4 py-10">
        <div className="mb-8">
          <Link
            href={`/interview/sessions/${sessionId}`}
            className="text-xs text-zinc-400 hover:text-zinc-600"
          >
            ← 세션으로 돌아가기
          </Link>
          <h1 className="mt-2 text-2xl font-semibold text-zinc-900">면접 결과 리포트</h1>
          <p className="mt-2 text-sm text-zinc-500">
            세션 종료 직후에는 결과가 바로 준비되지 않을 수 있습니다. 잠깐 자동으로 다시 확인한 뒤, 더 오래 걸리면 직접 결과를 다시 확인할 수 있습니다.
          </p>
        </div>

        <InterviewPendingResultPanel
          message={pendingMessage}
          onRefresh={handleManualRefresh}
          refreshing={refreshingResult}
          backHref={`/interview/sessions/${sessionId}`}
          backLabel="세션 다시 보기"
          autoRefreshActive={autoRefreshActive}
          autoRefreshAttempt={autoRefreshAttempt}
          autoRefreshMaxAttempts={PENDING_RESULT_AUTO_RECHECK_MAX_ATTEMPTS}
        />
      </main>
    );
  }

  if (!result) {
    return null;
  }

  return (
    <main>
      <InterviewResultReport
        result={result}
        backHref={`/interview/sessions/${sessionId}`}
        backLabel="세션으로 돌아가기"
        onRefresh={() => void loadResult({ showPageLoading: false, reason: 'manual' })}
      />
    </main>
  );
}
