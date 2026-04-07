'use client';

import { useSearchParams, useRouter } from 'next/navigation';
import { Suspense, useState } from 'react';
import { submitAnswer } from '@/api/practice';
import type { PracticeSessionResponse } from '@/types/practice';

export default function PracticeSolvePage() {
  return (
    <Suspense fallback={<div className="py-12 text-center text-zinc-400">불러오는 중...</div>}>
      <PracticeSolveContent />
    </Suspense>
  );
}

function PracticeSolveContent() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const knowledgeItemId = Number(searchParams.get('id'));
  const title = searchParams.get('title') ?? '';
  const questionType = searchParams.get('type') ?? 'cs';

  const [answer, setAnswer] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<PracticeSessionResponse | null>(null);
  const [error, setError] = useState('');

  async function handleSubmit() {
    if (answer.trim().length < 50) {
      setError('답변은 50자 이상 작성해주세요.');
      return;
    }
    setError('');
    setSubmitting(true);
    try {
      const res = await submitAnswer({ knowledgeItemId, answerText: answer });
      setResult(res);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '평가에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  function scoreColor(score: number) {
    if (score >= 80) return 'text-green-600';
    if (score >= 60) return 'text-amber-600';
    return 'text-red-600';
  }

  function scoreBg(score: number) {
    if (score >= 80) return 'bg-green-50 border-green-200';
    if (score >= 60) return 'bg-amber-50 border-amber-200';
    return 'bg-red-50 border-red-200';
  }

  if (!knowledgeItemId) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-12 text-center">
        <p className="text-zinc-500">잘못된 접근입니다.</p>
        <button
          onClick={() => router.push('/practice')}
          className="mt-4 text-sm text-blue-600 underline"
        >
          문제 목록으로 돌아가기
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      {/* 질문 헤더 */}
      <div className="mb-6">
        <span
          className={`mb-2 inline-block rounded px-2 py-0.5 text-xs font-medium ${
            questionType === 'behavioral'
              ? 'bg-amber-100 text-amber-700'
              : 'bg-blue-100 text-blue-700'
          }`}
        >
          {questionType === 'behavioral' ? '인성' : 'CS'}
        </span>
        <h1 className="text-xl font-bold text-zinc-900">{title}</h1>
      </div>

      {!result ? (
        /* 답변 작성 영역 */
        <div>
          <label className="mb-2 block text-sm font-medium text-zinc-700">
            답변을 작성하세요
          </label>
          <textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            disabled={submitting}
            placeholder="면접에서 답변하듯이 작성해보세요. (50자 이상)"
            className="w-full rounded-lg border border-zinc-300 p-4 text-sm leading-relaxed focus:border-blue-400 focus:outline-none disabled:bg-zinc-50"
            rows={10}
          />
          <div className="mt-2 flex items-center justify-between">
            <span className={`text-xs ${answer.length < 50 ? 'text-zinc-400' : 'text-zinc-500'}`}>
              {answer.length}자
            </span>
            <button
              onClick={handleSubmit}
              disabled={submitting || answer.trim().length < 50}
              className="rounded-lg bg-blue-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {submitting ? (
                <span className="flex items-center gap-2">
                  <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24">
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                      fill="none"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                    />
                  </svg>
                  AI가 평가하고 있습니다...
                </span>
              ) : (
                '제출 및 평가받기'
              )}
            </button>
          </div>

          {error && (
            <div className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</div>
          )}
        </div>
      ) : (
        /* 평가 결과 영역 */
        <div className="space-y-6">
          {/* 점수 */}
          <div
            className={`flex items-center gap-4 rounded-lg border p-6 ${scoreBg(result.score ?? 0)}`}
          >
            <div className="text-center">
              <div className={`text-4xl font-bold ${scoreColor(result.score ?? 0)}`}>
                {result.score}
              </div>
              <div className="text-xs text-zinc-500">/ 100점</div>
            </div>
            {result.tagNames.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {result.tagNames.map((tag) => (
                  <span
                    key={tag}
                    className="rounded-full bg-white/80 px-3 py-1 text-xs font-medium text-zinc-600 shadow-sm"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* 피드백 */}
          <div className="rounded-lg border border-zinc-200 p-5">
            <h3 className="mb-2 text-sm font-semibold text-zinc-800">평가 피드백</h3>
            <p className="whitespace-pre-line text-sm leading-relaxed text-zinc-600">
              {result.feedback}
            </p>
          </div>

          {/* 모범답안 */}
          <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-5">
            <h3 className="mb-2 text-sm font-semibold text-zinc-800">
              {questionType === 'behavioral' ? '답변 가이드' : '모범 답안'}
            </h3>
            <p className="whitespace-pre-line text-sm leading-relaxed text-zinc-600">
              {result.modelAnswer}
            </p>
          </div>

          {/* 내 답변 */}
          <div className="rounded-lg border border-zinc-200 p-5">
            <h3 className="mb-2 text-sm font-semibold text-zinc-800">내 답변</h3>
            <p className="whitespace-pre-line text-sm leading-relaxed text-zinc-500">{answer}</p>
          </div>

          {/* 하단 버튼 */}
          <div className="flex gap-3">
            <button
              onClick={() => router.push('/practice')}
              className="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
            >
              다른 문제 풀기
            </button>
            <button
              onClick={() => router.push('/practice/history')}
              className="rounded-lg border border-zinc-300 px-5 py-2.5 text-sm font-medium hover:bg-zinc-50"
            >
              이력 보기
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
