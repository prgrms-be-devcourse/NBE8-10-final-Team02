'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getSessions, getSessionDetail } from '@/api/practice';
import type {
  PracticeSessionResponse,
  PracticeSessionDetailResponse,
  PracticePagination,
} from '@/types/practice';

export default function PracticeHistoryPage() {
  const router = useRouter();
  const [sessions, setSessions] = useState<PracticeSessionResponse[]>([]);
  const [pagination, setPagination] = useState<PracticePagination | null>(null);
  const [questionType, setQuestionType] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<PracticeSessionDetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    setError('');
    getSessions({ questionType: questionType || undefined, page, size: 20 })
      .then(({ data, pagination: p }) => {
        setSessions(data);
        setPagination(p);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [questionType, page]);

  async function handleDetail(sessionId: number) {
    if (detail?.sessionId === sessionId) {
      setDetail(null);
      return;
    }
    setDetailLoading(true);
    try {
      const d = await getSessionDetail(sessionId);
      setDetail(d);
    } catch {
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  }

  function scoreColor(score: number | null) {
    if (score == null) return 'text-zinc-400';
    if (score >= 80) return 'text-green-600';
    if (score >= 60) return 'text-amber-600';
    return 'text-red-600';
  }

  function formatDate(iso: string) {
    return new Date(iso).toLocaleDateString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">연습 이력</h1>
        <button
          onClick={() => router.push('/practice')}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          문제 풀기
        </button>
      </div>

      {/* 유형 필터 */}
      <div className="mb-4 flex gap-2">
        {[
          { label: '전체', value: '' },
          { label: 'CS', value: 'cs' },
          { label: '인성', value: 'behavioral' },
        ].map((opt) => (
          <button
            key={opt.value}
            onClick={() => { setQuestionType(opt.value); setPage(0); }}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
              questionType === opt.value
                ? 'bg-zinc-900 text-white'
                : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {error && (
        <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</div>
      )}

      {loading ? (
        <div className="py-12 text-center text-zinc-400">불러오는 중...</div>
      ) : sessions.length === 0 ? (
        <div className="py-12 text-center text-zinc-400">연습 이력이 없습니다.</div>
      ) : (
        <div className="space-y-3">
          {sessions.map((s) => (
            <div key={s.sessionId}>
              <button
                onClick={() => handleDetail(s.sessionId)}
                className="block w-full rounded-lg border border-zinc-200 p-4 text-left transition hover:border-zinc-300 hover:bg-zinc-50/50"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span
                      className={`rounded px-2 py-0.5 text-xs font-medium ${
                        s.questionType === 'behavioral'
                          ? 'bg-amber-100 text-amber-700'
                          : 'bg-blue-100 text-blue-700'
                      }`}
                    >
                      {s.questionType === 'behavioral' ? '인성' : 'CS'}
                    </span>
                    <span className="text-sm font-medium text-zinc-800">{s.questionTitle}</span>
                  </div>
                  <div className="flex items-center gap-3">
                    {s.status === 'evaluated' && s.score != null && (
                      <span className={`text-lg font-bold ${scoreColor(s.score)}`}>
                        {s.score}점
                      </span>
                    )}
                    {s.status === 'failed' && (
                      <span className="rounded bg-red-100 px-2 py-0.5 text-xs text-red-600">
                        평가 실패
                      </span>
                    )}
                    <span className="text-xs text-zinc-400">{formatDate(s.createdAt)}</span>
                  </div>
                </div>
                {s.tagNames.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1">
                    {s.tagNames.map((tag) => (
                      <span key={tag} className="rounded bg-zinc-100 px-2 py-0.5 text-xs text-zinc-500">
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
              </button>

              {/* 상세 확장 */}
              {detail?.sessionId === s.sessionId && (
                <div className="mt-1 space-y-3 rounded-b-lg border border-t-0 border-zinc-200 bg-zinc-50 p-5">
                  {detailLoading ? (
                    <div className="text-sm text-zinc-400">불러오는 중...</div>
                  ) : (
                    <>
                      {detail.feedback && (
                        <div>
                          <h4 className="mb-1 text-xs font-semibold text-zinc-500">피드백</h4>
                          <p className="whitespace-pre-line text-sm text-zinc-600">
                            {detail.feedback}
                          </p>
                        </div>
                      )}
                      {detail.modelAnswer && (
                        <div>
                          <h4 className="mb-1 text-xs font-semibold text-zinc-500">
                            {detail.questionType === 'behavioral' ? '답변 가이드' : '모범 답안'}
                          </h4>
                          <p className="whitespace-pre-line text-sm text-zinc-600">
                            {detail.modelAnswer}
                          </p>
                        </div>
                      )}
                      {detail.answerText && (
                        <div>
                          <h4 className="mb-1 text-xs font-semibold text-zinc-500">내 답변</h4>
                          <p className="whitespace-pre-line text-sm text-zinc-500">
                            {detail.answerText}
                          </p>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* 페이지네이션 */}
      {pagination && pagination.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-2">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="rounded border px-3 py-1.5 text-sm disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-sm text-zinc-500">
            {page + 1} / {pagination.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(pagination.totalPages - 1, p + 1))}
            disabled={page >= pagination.totalPages - 1}
            className="rounded border px-3 py-1.5 text-sm disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}
