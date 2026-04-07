'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { getSessionResult, InterviewApiError } from '@/api/interview';
import InterviewPendingResultPanel from '@/components/InterviewPendingResultPanel';
import InterviewResultReport from '@/components/InterviewResultReport';
import type { InterviewResult } from '@/types/interview';

export default function InterviewSessionResultPage() {
  const params = useParams();
  const sessionId = Number(params.sessionId);

  const [result, setResult] = useState<InterviewResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingMessage, setPendingMessage] = useState<string | null>(null);
  const [refreshingResult, setRefreshingResult] = useState(false);

  const loadResult = useCallback(async (options?: { showPageLoading?: boolean }) => {
    if (!Number.isFinite(sessionId)) {
      setError('올바른 결과 경로가 아닙니다.');
      setLoading(false);
      return;
    }

    const showPageLoading = options?.showPageLoading ?? true;
    if (showPageLoading) {
      setLoading(true);
    } else {
      setRefreshingResult(true);
    }
    setError(null);
    setPendingMessage(null);

    try {
      const data = await getSessionResult(sessionId);
      setResult(data);
    } catch (err) {
      setResult(null);

      if (err instanceof InterviewApiError && err.code === 'INTERVIEW_RESULT_INCOMPLETE') {
        setPendingMessage(err.message);
      } else {
        setError(err instanceof Error ? err.message : '면접 결과를 불러오지 못했습니다.');
      }
    } finally {
      if (showPageLoading) {
        setLoading(false);
      } else {
        setRefreshingResult(false);
      }
    }
  }, [sessionId]);

  useEffect(() => {
    void loadResult();
  }, [loadResult]);

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
            세션 종료 직후에는 결과가 바로 준비되지 않을 수 있습니다. 결과가 준비되면 답변된 꼬리질문도 같은 리포트에 함께 표시됩니다.
          </p>
        </div>

        <InterviewPendingResultPanel
          message={pendingMessage}
          onRefresh={() => void loadResult({ showPageLoading: false })}
          refreshing={refreshingResult}
          backHref={`/interview/sessions/${sessionId}`}
          backLabel="세션 다시 보기"
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
        onRefresh={() => void loadResult({ showPageLoading: false })}
      />
    </main>
  );
}
