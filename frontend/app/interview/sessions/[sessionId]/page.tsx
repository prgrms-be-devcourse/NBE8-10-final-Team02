'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { getSessionDetail } from '@/api/interview';
import type { InterviewSessionDetail } from '@/types/interview';

const STATUS_LABEL: Record<InterviewSessionDetail['status'], string> = {
  ready: '준비',
  in_progress: '진행 중',
  paused: '일시정지',
  completed: '종료',
  feedback_completed: '피드백 완료',
};

export default function InterviewSessionPage() {
  const params = useParams();
  const sessionId = Number(params.sessionId);

  const [session, setSession] = useState<InterviewSessionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSession = useCallback(async () => {
    if (!Number.isFinite(sessionId)) {
      setError('올바른 세션 경로가 아닙니다.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const data = await getSessionDetail(sessionId);
      setSession(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '세션 정보를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    void loadSession();
  }, [loadSession]);

  if (loading) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-12">
        <p className="text-sm text-zinc-400">세션 정보를 불러오는 중...</p>
      </main>
    );
  }

  if (error || !session) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-12">
        <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error ?? '세션을 찾을 수 없습니다.'}
        </div>
        <button
          type="button"
          onClick={() => void loadSession()}
          className="mt-4 text-sm font-medium text-zinc-500 underline"
        >
          다시 시도
        </button>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <div className="mb-8">
        <Link
          href={`/interview/question-sets/${session.questionSetId}`}
          className="text-xs text-zinc-400 hover:text-zinc-600"
        >
          ← 질문 세트 상세로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold text-zinc-900">모의 면접 세션</h1>
        <p className="mt-2 text-sm text-zinc-500">
          세션 시작은 연결됐고, 답변 제출 UI는 다음 단계에서 이어서 붙입니다.
        </p>
      </div>

      <section className="rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
            상태 {STATUS_LABEL[session.status]}
          </span>
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
            진행 {session.answeredQuestionCount}/{session.totalQuestionCount}
          </span>
        </div>

        {session.currentQuestion ? (
          <div className="mt-5 rounded-2xl border border-zinc-200 px-4 py-4">
            <p className="text-xs font-medium text-zinc-500">현재 질문</p>
            <p className="mt-2 text-sm font-medium text-zinc-900">
              Q{session.currentQuestion.questionOrder}. {session.currentQuestion.questionText}
            </p>
            <div className="mt-2 flex flex-wrap gap-2 text-xs text-zinc-500">
              <span>{session.currentQuestion.questionType}</span>
              <span>{session.currentQuestion.difficultyLevel}</span>
            </div>
          </div>
        ) : (
          <div className="mt-5 rounded-2xl border border-zinc-200 px-4 py-4 text-sm text-zinc-500">
            현재 진행 중인 질문이 없습니다.
          </div>
        )}

        <div className="mt-5 grid gap-3 md:grid-cols-2">
          <div className="rounded-xl bg-zinc-50 px-4 py-3">
            <p className="text-xs text-zinc-500">남은 질문</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">{session.remainingQuestionCount}개</p>
          </div>
          <div className="rounded-xl bg-zinc-50 px-4 py-3">
            <p className="text-xs text-zinc-500">세션 복원 가능</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">
              {session.resumeAvailable ? '가능' : '불가'}
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
