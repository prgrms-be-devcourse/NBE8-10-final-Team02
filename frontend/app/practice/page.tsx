'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getQuestions, getRandomQuestions, getTags } from '@/api/practice';
import type { PracticeQuestion, PracticeTag, PracticePagination } from '@/types/practice';

export default function PracticePage() {
  const router = useRouter();
  const [questions, setQuestions] = useState<PracticeQuestion[]>([]);
  const [tags, setTags] = useState<PracticeTag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  const [questionType, setQuestionType] = useState<string>('');
  const [pagination, setPagination] = useState<PracticePagination | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getTags().then(setTags).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    setError('');
    getQuestions({ tagIds: selectedTagIds, questionType: questionType || undefined, page, size: 20 })
      .then(({ data, pagination: p }) => {
        setQuestions(data);
        setPagination(p);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [selectedTagIds, questionType, page]);

  function toggleTag(tagId: number) {
    setSelectedTagIds((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    );
    setPage(0);
  }

  async function handleRandom() {
    setError('');
    try {
      const items = await getRandomQuestions({
        tagIds: selectedTagIds,
        questionType: questionType || undefined,
        count: 1,
      });
      if (items.length > 0) {
        router.push(`/practice/solve?id=${items[0].knowledgeItemId}&title=${encodeURIComponent(items[0].questionText)}&type=${items[0].questionType}`);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '랜덤 출제에 실패했습니다.');
    }
  }

  function handleSelect(q: PracticeQuestion) {
    router.push(`/practice/solve?id=${q.knowledgeItemId}&title=${encodeURIComponent(q.questionText)}&type=${q.questionType}`);
  }

  const topicTags = tags.filter((t) => t.category === 'topic');
  const languageTags = tags.filter((t) => t.category === 'language');

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">문제 연습</h1>
        <div className="flex gap-2">
          <button
            onClick={handleRandom}
            className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            랜덤 출제
          </button>
          <button
            onClick={() => router.push('/practice/history')}
            className="rounded-lg border border-zinc-300 px-4 py-2 text-sm font-medium hover:bg-zinc-50"
          >
            이력 보기
          </button>
        </div>
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

      {/* 태그 필터 */}
      {topicTags.length > 0 && (
        <div className="mb-2">
          <span className="mr-2 text-xs font-medium text-zinc-500">주제</span>
          <div className="inline-flex flex-wrap gap-1.5">
            {topicTags.map((tag) => (
              <button
                key={tag.id}
                onClick={() => toggleTag(tag.id)}
                className={`rounded-full px-3 py-1 text-xs transition ${
                  selectedTagIds.includes(tag.id)
                    ? 'bg-blue-100 text-blue-700'
                    : 'bg-zinc-100 text-zinc-500 hover:bg-zinc-200'
                }`}
              >
                {tag.name}
              </button>
            ))}
          </div>
        </div>
      )}
      {languageTags.length > 0 && (
        <div className="mb-4">
          <span className="mr-2 text-xs font-medium text-zinc-500">언어</span>
          <div className="inline-flex flex-wrap gap-1.5">
            {languageTags.map((tag) => (
              <button
                key={tag.id}
                onClick={() => toggleTag(tag.id)}
                className={`rounded-full px-3 py-1 text-xs transition ${
                  selectedTagIds.includes(tag.id)
                    ? 'bg-blue-100 text-blue-700'
                    : 'bg-zinc-100 text-zinc-500 hover:bg-zinc-200'
                }`}
              >
                {tag.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {error && (
        <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-600">{error}</div>
      )}

      {/* 질문 목록 */}
      {loading ? (
        <div className="py-12 text-center text-zinc-400">불러오는 중...</div>
      ) : questions.length === 0 ? (
        <div className="py-12 text-center text-zinc-400">질문이 없습니다.</div>
      ) : (
        <div className="space-y-3">
          {questions.map((q) => (
            <button
              key={q.knowledgeItemId}
              onClick={() => handleSelect(q)}
              className="block w-full rounded-lg border border-zinc-200 p-4 text-left transition hover:border-blue-300 hover:bg-blue-50/30"
            >
              <div className="mb-1.5 flex items-center gap-2">
                <span
                  className={`rounded px-2 py-0.5 text-xs font-medium ${
                    q.questionType === 'behavioral'
                      ? 'bg-amber-100 text-amber-700'
                      : 'bg-blue-100 text-blue-700'
                  }`}
                >
                  {q.questionType === 'behavioral' ? '인성' : 'CS'}
                </span>
                <span className="text-sm font-medium text-zinc-800">{q.questionText}</span>
              </div>
              {q.tags.length > 0 && (
                <div className="flex flex-wrap gap-1">
                  {q.tags.map((tag) => (
                    <span key={tag.id} className="rounded bg-zinc-100 px-2 py-0.5 text-xs text-zinc-500">
                      {tag.name}
                    </span>
                  ))}
                </div>
              )}
            </button>
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
