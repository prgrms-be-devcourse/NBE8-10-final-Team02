'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { getSessionDetail, getSessionResult, InterviewApiError } from '@/api/interview';
import InterviewPendingResultPanel from '@/components/InterviewPendingResultPanel';
import InterviewResultReport from '@/components/InterviewResultReport';
import {
  PENDING_RESULT_PANEL_COPY,
  SESSION_STATUS_BADGE_META,
} from '@/lib/interview-status-ui';
import type { InterviewResult, InterviewSessionDetail, InterviewSessionStatus } from '@/types/interview';

const ACTIVE_STATUSES = new Set<InterviewSessionStatus>(['in_progress', 'paused']);

function formatDateTime(value: string | null) {
  if (!value) {
    return '기록 없음';
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

export default function InterviewHistoryDetailPage() {
  const params = useParams();
  const sessionId = Number(params.sessionId);

  const [session, setSession] = useState<InterviewSessionDetail | null>(null);
  const [result, setResult] = useState<InterviewResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingMessage, setPendingMessage] = useState<string | null>(null);
  const [refreshingResult, setRefreshingResult] = useState(false);

  const loadResultOnly = useCallback(async () => {
    setRefreshingResult(true);
    setError(null);
    setPendingMessage(null);

    try {
      const data = await getSessionResult(sessionId);
      setResult(data);
      setSession((current) => (current ? { ...current, status: data.status } : current));
    } catch (err) {
      setResult(null);

      if (err instanceof InterviewApiError && err.code === 'INTERVIEW_RESULT_INCOMPLETE') {
        setPendingMessage(err.message);
      } else {
        setError(err instanceof Error ? err.message : '면접 결과를 불러오지 못했습니다.');
      }
    } finally {
      setRefreshingResult(false);
    }
  }, [sessionId]);

  const loadHistoryDetail = useCallback(async () => {
    if (!Number.isFinite(sessionId)) {
      setError('올바른 히스토리 경로가 아닙니다.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    setPendingMessage(null);

    try {
      const detail = await getSessionDetail(sessionId);
      setSession(detail);

      if (ACTIVE_STATUSES.has(detail.status)) {
        setResult(null);
        return;
      }

      try {
        const data = await getSessionResult(sessionId);
        setResult(data);
        setSession((current) => (current ? { ...current, status: data.status } : current));
      } catch (err) {
        setResult(null);

        if (err instanceof InterviewApiError && err.code === 'INTERVIEW_RESULT_INCOMPLETE') {
          setPendingMessage(err.message);
        } else {
          setError(err instanceof Error ? err.message : '면접 결과를 불러오지 못했습니다.');
        }
      }
    } catch (err) {
      setSession(null);
      setResult(null);
      setError(err instanceof Error ? err.message : '히스토리 상세를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    void loadHistoryDetail();
  }, [loadHistoryDetail]);

  if (loading) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-12">
        <p className="text-sm text-zinc-400">히스토리 상세를 불러오는 중...</p>
      </main>
    );
  }

  if (error && !session) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-12">
        <div className="rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-700">
          {error}
        </div>
        <button
          type="button"
          onClick={() => void loadHistoryDetail()}
          className="mt-4 rounded-full bg-zinc-900 px-4 py-2 text-sm font-medium text-white"
        >
          다시 시도
        </button>
      </main>
    );
  }

  if (!session) {
    return null;
  }

  const isActiveSession = ACTIVE_STATUSES.has(session.status);

  return (
    <main className="mx-auto max-w-5xl px-4 py-10">
      <div className="mb-8">
        <Link href="/interview/history" className="text-xs text-zinc-400 hover:text-zinc-600">
          ← 면접 히스토리로 돌아가기
        </Link>
        <h1 className="mt-2 text-2xl font-semibold text-zinc-900">면접 히스토리 상세</h1>
        <p className="mt-2 text-sm text-zinc-500">
          세션 기본 정보와 결과 재진입 상태를 다시 확인합니다. 결과가 준비되면 답변된 꼬리질문도 같은 흐름 안에서 함께 확인합니다.
        </p>
      </div>

      <section className="rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${SESSION_STATUS_BADGE_META[session.status].tone}`}
          >
            {SESSION_STATUS_BADGE_META[session.status].label}
          </span>
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
            세션 #{session.id}
          </span>
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
            질문 세트 #{session.questionSetId}
          </span>
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">진행률</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">
              {session.answeredQuestionCount}/{session.totalQuestionCount}
            </p>
          </div>
          <div className="rounded-xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">남은 질문</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">{session.remainingQuestionCount}개</p>
          </div>
          <div className="rounded-xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">시작 시각</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">{formatDateTime(session.startedAt)}</p>
          </div>
          <div className="rounded-xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">종료 시각</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">{formatDateTime(session.endedAt)}</p>
          </div>
        </div>

        {session.lastActivityAt && (
          <p className="mt-4 text-sm text-zinc-500">
            마지막 활동 시각: {formatDateTime(session.lastActivityAt)}
          </p>
        )}
      </section>

      {isActiveSession && (
        <section className="mt-6 rounded-2xl border border-green-200 bg-green-50 px-5 py-5 shadow-sm">
          <p className="text-sm font-semibold text-green-900">
            {session.status === 'paused' ? '현재 일시정지된 세션입니다.' : '현재 진행 중인 세션입니다.'}
          </p>
          <p className="mt-2 text-sm leading-6 text-green-800">
            {session.status === 'paused'
              ? '이 상세 화면에서는 과거 결과 대신 세션 복귀가 우선입니다. 같은 질문부터 다시 이어서 진행하려면 세션으로 돌아가 재개하세요.'
              : '이 상세 화면에서는 과거 결과 대신 세션 복귀가 우선입니다. 현재 질문은 세션 화면의 currentQuestion 기준으로 이어집니다.'}
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <Link
              href={`/interview/sessions/${session.id}`}
              className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white"
            >
              {session.status === 'paused' ? '세션 재개' : '세션 이어서 진행'}
            </Link>
            <Link
              href="/interview/history"
              className="rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-medium text-zinc-700"
            >
              목록으로 돌아가기
            </Link>
          </div>
        </section>
      )}

      {!isActiveSession && pendingMessage && (
        <div className="mt-6">
          <InterviewPendingResultPanel
            message={pendingMessage}
            onRefresh={() => void loadResultOnly()}
            refreshing={refreshingResult}
            backHref="/interview/history"
            backLabel="목록으로 돌아가기"
          />
          <p className="mt-3 text-sm text-cyan-900">
            {PENDING_RESULT_PANEL_COPY.actionLabel}이 성공하면 같은 화면에서 질문, 답변, 피드백이 바로 이어서 표시됩니다.
          </p>
        </div>
      )}

      {!isActiveSession && error && (
        <section className="mt-6 rounded-2xl border border-red-200 bg-red-50 px-5 py-5 shadow-sm text-sm text-red-700">
          {error}
        </section>
      )}

      {!isActiveSession && result && (
        <InterviewResultReport
          result={result}
          backHref="/interview/history"
          backLabel="면접 히스토리로 돌아가기"
          title="면접 히스토리 상세"
          description="과거 세션의 질문, 답변, 피드백을 다시 확인합니다."
          refreshLabel="결과 다시 보기"
          onRefresh={() => void loadResultOnly()}
          showHeader={false}
        />
      )}
    </main>
  );
}
