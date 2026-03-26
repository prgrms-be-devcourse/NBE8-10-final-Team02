'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getSessions } from '@/api/interview';
import type { InterviewSession, InterviewSessionStatus } from '@/types/interview';

type HistoryFilter = 'all' | 'pending' | 'completed';

const FILTER_OPTIONS: Array<{ value: HistoryFilter; label: string }> = [
  { value: 'all', label: '전체' },
  { value: 'pending', label: '결과 준비 중' },
  { value: 'completed', label: '결과 완료' },
];

const ACTIVE_STATUSES = new Set<InterviewSessionStatus>(['in_progress', 'paused']);

const STATUS_LABEL: Record<InterviewSessionStatus, string> = {
  ready: '준비',
  in_progress: '진행 중',
  paused: '일시정지',
  completed: '결과 준비 중',
  feedback_completed: '결과 완료',
};

const STATUS_TONE: Record<InterviewSessionStatus, string> = {
  ready: 'bg-zinc-100 text-zinc-700',
  in_progress: 'bg-green-50 text-green-700',
  paused: 'bg-amber-50 text-amber-700',
  completed: 'bg-amber-50 text-amber-700',
  feedback_completed: 'bg-blue-50 text-blue-700',
};

function formatDateTime(value: string | null) {
  if (!value) {
    return '기록 없음';
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

export default function InterviewHistoryPage() {
  const [sessions, setSessions] = useState<InterviewSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<HistoryFilter>('all');

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await getSessions();
      setSessions(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '면접 히스토리를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  const activeSession = useMemo(
    () => sessions.find((session) => ACTIVE_STATUSES.has(session.status)),
    [sessions],
  );

  const historicalSessions = useMemo(
    () => sessions.filter((session) => session.id !== activeSession?.id),
    [activeSession?.id, sessions],
  );

  const filteredSessions = useMemo(() => {
    if (filter === 'pending') {
      return historicalSessions.filter((session) => session.status === 'completed');
    }

    if (filter === 'completed') {
      return historicalSessions.filter((session) => session.status === 'feedback_completed');
    }

    return historicalSessions;
  }, [filter, historicalSessions]);

  if (loading) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-12">
        <p className="text-sm text-zinc-400">면접 히스토리를 불러오는 중...</p>
      </main>
    );
  }

  if (error) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-12">
        <div className="rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-700">
          {error}
        </div>
        <button
          type="button"
          onClick={() => void loadSessions()}
          className="mt-4 rounded-full bg-zinc-900 px-4 py-2 text-sm font-medium text-white"
        >
          다시 시도
        </button>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-5xl px-4 py-10">
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-900">면접 히스토리</h1>
          <p className="mt-2 text-sm text-zinc-500">
            현재 활성 세션과 과거 세션 기록을 한 화면에서 다시 확인합니다.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void loadSessions()}
          className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700"
        >
          새로고침
        </button>
      </div>

      {activeSession && (
        <section className="mb-6 rounded-2xl border border-green-200 bg-green-50 px-5 py-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs font-medium text-green-700">활성 세션</p>
              <h2 className="mt-1 text-lg font-semibold text-green-900">
                질문 세트 #{activeSession.questionSetId}
              </h2>
              <div className="mt-3 flex flex-wrap items-center gap-2 text-sm text-green-900">
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_TONE[activeSession.status]}`}>
                  {STATUS_LABEL[activeSession.status]}
                </span>
                <span>시작 {formatDateTime(activeSession.startedAt)}</span>
              </div>
            </div>

            <Link
              href={`/interview/sessions/${activeSession.id}`}
              className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white"
            >
              {activeSession.status === 'paused' ? '세션 재개' : '세션 이어서 진행'}
            </Link>
          </div>
        </section>
      )}

      <section className="rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs font-medium text-zinc-500">과거 세션 목록</p>
            <h2 className="mt-1 text-lg font-semibold text-zinc-900">이전 기록 다시 보기</h2>
          </div>

          <div className="flex flex-wrap gap-2">
            {FILTER_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setFilter(option.value)}
                className={`rounded-full px-3 py-1.5 text-sm font-medium ${
                  filter === option.value
                    ? 'bg-zinc-900 text-white'
                    : 'border border-zinc-300 text-zinc-700'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        {filteredSessions.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-zinc-200 px-5 py-10 text-sm text-zinc-500">
            {historicalSessions.length === 0
              ? '아직 종료된 면접 세션 기록이 없습니다.'
              : '선택한 필터에 맞는 세션이 없습니다.'}
          </div>
        ) : (
          <div className="space-y-3">
            {filteredSessions.map((session) => (
              <article key={session.id} className="rounded-2xl border border-zinc-200 px-4 py-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_TONE[session.status]}`}>
                        {STATUS_LABEL[session.status]}
                      </span>
                      <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
                        질문 세트 #{session.questionSetId}
                      </span>
                    </div>

                    <div className="mt-3 grid gap-2 text-sm text-zinc-600 md:grid-cols-2">
                      <p>시작 시각: {formatDateTime(session.startedAt)}</p>
                      <p>종료 시각: {formatDateTime(session.endedAt)}</p>
                    </div>

                    {session.status === 'feedback_completed' ? (
                      <div className="mt-3 rounded-xl bg-blue-50 px-4 py-3 text-sm text-blue-900">
                        <p className="font-medium">최근 점수 {session.totalScore}</p>
                        {session.summaryFeedback && (
                          <p className="mt-1 line-clamp-2">{session.summaryFeedback}</p>
                        )}
                      </div>
                    ) : (
                      <div className="mt-3 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">
                        결과가 아직 준비 중일 수 있습니다. 상세 화면에서 다시 확인할 수 있습니다.
                      </div>
                    )}
                  </div>

                  <Link
                    href={`/interview/history/${session.id}`}
                    className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700"
                  >
                    상세 보기
                  </Link>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
