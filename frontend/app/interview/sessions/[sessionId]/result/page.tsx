'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { getSessionResult, InterviewApiError } from '@/api/interview';
import InterviewResultReport from '@/components/InterviewResultReport';
import type { InterviewResult } from '@/types/interview';

export default function InterviewSessionResultPage() {
  const params = useParams();
  const sessionId = Number(params.sessionId);

  const [result, setResult] = useState<InterviewResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingMessage, setPendingMessage] = useState<string | null>(null);

  const loadResult = useCallback(async () => {
    if (!Number.isFinite(sessionId)) {
      setError('올바른 결과 경로가 아닙니다.');
      setLoading(false);
      return;
    }

    setLoading(true);
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
      setLoading(false);
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
            세션은 종료됐지만 결과 생성이 아직 끝나지 않았을 수 있습니다.
          </p>
        </div>

        <section className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-5 shadow-sm">
          <p className="text-sm font-semibold text-amber-900">결과 준비 중</p>
          <p className="mt-2 text-sm leading-6 text-amber-800">{pendingMessage}</p>
          <p className="mt-2 text-sm text-amber-800">
            v1에서는 세션 종료를 다시 보내지 않고 이 화면에서 결과를 다시 확인합니다.
          </p>

          <div className="mt-5 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => void loadResult()}
              className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white"
            >
              다시 확인
            </button>
            <Link
              href={`/interview/sessions/${sessionId}`}
              className="rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-medium text-zinc-700"
            >
              세션 다시 보기
            </Link>
          </div>
        </section>
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
        onRefresh={() => void loadResult()}
      />
    </main>
  );
}
