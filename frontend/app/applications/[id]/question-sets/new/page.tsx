'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { getApplication } from '@/api/application';
import { createQuestionSet, getQuestionSets } from '@/api/interview';
import type { Application } from '@/types/application';
import type {
  InterviewDifficultyLevel,
  InterviewQuestionSetSummary,
  InterviewQuestionType,
} from '@/types/interview';

// 난이도 옵션
const DIFFICULTY_OPTIONS: Array<{ value: InterviewDifficultyLevel; label: string }> = [
  { value: 'easy', label: '쉬움' },
  { value: 'medium', label: '보통' },
  { value: 'hard', label: '어려움' },
];

// 질문 유형 옵션 (follow_up은 AI가 자동 생성하므로 선택지에서 제외)
const QUESTION_TYPE_OPTIONS: Array<{ value: InterviewQuestionType; label: string; description: string }> = [
  { value: 'experience', label: '경험', description: '과거 경험 기반 질문' },
  { value: 'project', label: '프로젝트', description: '프로젝트 관련 심층 질문' },
  { value: 'technical_cs', label: 'CS 기초', description: '컴퓨터 과학 기초 질문' },
  { value: 'technical_stack', label: '기술 스택', description: '사용 기술 관련 질문' },
  { value: 'behavioral', label: '행동', description: '상황 대처 능력 질문' },
];

// 난이도 라벨 매핑
const DIFFICULTY_LABEL: Record<InterviewDifficultyLevel, string> = {
  easy: '쉬움',
  medium: '보통',
  hard: '어려움',
};

export default function NewQuestionSetPage() {
  const params = useParams();
  const router = useRouter();
  const applicationId = Number(params.id);

  // 지원 준비 정보
  const [application, setApplication] = useState<Application | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 기존 질문 세트 목록 (이 지원 준비에 속한 것만 필터)
  const [existingSets, setExistingSets] = useState<InterviewQuestionSetSummary[]>([]);

  // 생성 폼 상태
  const [title, setTitle] = useState('');
  const [questionCount, setQuestionCount] = useState(5);
  const [difficultyLevel, setDifficultyLevel] = useState<InterviewDifficultyLevel>('medium');
  const [selectedTypes, setSelectedTypes] = useState<Set<InterviewQuestionType>>(
    new Set(['experience', 'project', 'technical_stack']),
  );

  // 생성 처리 상태
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // ── 데이터 로딩 ─────────────────────────────────
  const loadPageData = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      // 지원 준비 정보와 기존 질문 세트를 병렬로 조회
      const [app, allSets] = await Promise.all([
        getApplication(applicationId),
        getQuestionSets(),
      ]);
      setApplication(app);
      // 현재 지원 준비에 속한 질문 세트만 필터링
      setExistingSets(allSets.filter((s) => s.applicationId === applicationId));
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '정보를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [applicationId]);

  useEffect(() => {
    loadPageData();
  }, [loadPageData]);

  // ── 질문 유형 토글 ────────────────────────────────
  function toggleQuestionType(type: InterviewQuestionType) {
    setSelectedTypes((prev) => {
      const next = new Set(prev);
      if (next.has(type)) {
        // 최소 1개는 선택해야 함
        if (next.size > 1) next.delete(type);
      } else {
        next.add(type);
      }
      return next;
    });
  }

  // ── AI 질문 세트 생성 ──────────────────────────────
  async function handleCreate() {
    if (selectedTypes.size === 0) return;

    setCreating(true);
    setCreateError(null);

    try {
      const result = await createQuestionSet({
        applicationId,
        ...(title.trim() && { title: title.trim() }),
        questionCount,
        difficultyLevel,
        questionTypes: Array.from(selectedTypes),
      });

      // 생성 완료 후 질문 세트 상세 페이지로 이동
      router.push(`/interview/question-sets/${result.questionSetId}`);
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : 'AI 질문 생성 중 오류가 발생했습니다.');
    } finally {
      setCreating(false);
    }
  }

  // ── 렌더링 ────────────────────────────────────────

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <p className="text-sm text-zinc-400">불러오는 중...</p>
      </main>
    );
  }

  if (loadError || !application) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {loadError ?? '지원 준비 정보를 찾을 수 없습니다.'}
        </div>
        <Link href="/applications" className="mt-4 inline-block text-sm underline text-zinc-500">
          지원 준비 목록으로
        </Link>
      </main>
    );
  }

  if (application.status !== 'ready') {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <h1 className="text-xl font-semibold">AI 면접 질문 생성</h1>
        <div className="mt-4 rounded border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          질문 생성은 지원 준비가 &quot;준비 완료&quot; 상태일 때만 가능합니다.
          소스 연결과 자소서 문항 답변을 먼저 완료해 주세요.
        </div>
        <Link
          href={`/applications/${applicationId}`}
          className="mt-4 inline-block text-sm underline text-zinc-500"
        >
          지원 준비 상세로
        </Link>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      {/* 헤더 */}
      <div className="mb-6">
        <Link
          href={`/applications/${applicationId}`}
          className="text-xs text-zinc-400 hover:text-zinc-600"
        >
          ← 지원 준비 상세로
        </Link>
        <h1 className="mt-2 text-xl font-semibold">AI 면접 질문 생성</h1>
        <p className="mt-1 text-sm text-zinc-500">
          {application.companyName && `${application.companyName} · `}
          {application.jobRole} — 포트폴리오 기반으로 AI가 면접 질문을 생성합니다.
        </p>
      </div>

      {/* 기존 질문 세트 목록 */}
      {existingSets.length > 0 && (
        <section className="mb-8">
          <h2 className="mb-3 text-sm font-medium text-zinc-700">
            기존 질문 세트 ({existingSets.length}개)
          </h2>
          <ul className="flex flex-col gap-2">
            {existingSets.map((set) => (
              <li key={set.questionSetId} className="rounded border border-zinc-200 px-4 py-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <button
                      onClick={() => router.push(`/interview/question-sets/${set.questionSetId}`)}
                      className="text-sm font-medium text-zinc-900 hover:underline text-left truncate"
                    >
                      {set.title}
                    </button>
                    <div className="mt-1 flex flex-wrap gap-x-3 text-xs text-zinc-500">
                      <span>질문 {set.questionCount}개</span>
                      <span>난이도 {DIFFICULTY_LABEL[set.difficultyLevel]}</span>
                      <span>{new Date(set.createdAt).toLocaleDateString('ko-KR')}</span>
                    </div>
                  </div>
                  <button
                    onClick={() => router.push(`/interview/question-sets/${set.questionSetId}`)}
                    className="shrink-0 rounded border border-zinc-300 px-3 py-1.5 text-xs font-medium text-zinc-700"
                  >
                    상세
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* 생성 폼 */}
      <section className="rounded border border-zinc-200 px-5 py-5">
        <h2 className="mb-4 text-sm font-medium text-zinc-700">새 질문 세트 생성</h2>

        <div className="flex flex-col gap-5">
          {/* 제목 (선택) */}
          <div>
            <label className="block text-xs text-zinc-500 mb-1">세트 제목 (선택)</label>
            <input
              type="text"
              placeholder="예: 네이버 백엔드 1차 기술면접"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full rounded border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none"
            />
          </div>

          {/* 질문 수 */}
          <div>
            <label className="block text-xs text-zinc-500 mb-1">
              질문 수 <span className="text-red-500">*</span>
            </label>
            <div className="flex items-center gap-3">
              <input
                type="range"
                min={1}
                max={20}
                value={questionCount}
                onChange={(e) => setQuestionCount(Number(e.target.value))}
                className="flex-1 accent-zinc-800"
              />
              <span className="w-12 text-center text-sm font-medium text-zinc-900">
                {questionCount}개
              </span>
            </div>
            <p className="mt-1 text-xs text-zinc-400">1~20개 (모의면접 세션은 3개 이상 필요)</p>
            {questionCount < selectedTypes.size && (
              <p className="mt-1 text-xs text-amber-600">
                질문 수({questionCount}개)가 선택한 유형({selectedTypes.size}개)보다 적어,
                일부 유형은 포함되지 않을 수 있습니다.
              </p>
            )}
          </div>

          {/* 난이도 */}
          <div>
            <label className="block text-xs text-zinc-500 mb-2">
              난이도 <span className="text-red-500">*</span>
            </label>
            <div className="flex gap-2">
              {DIFFICULTY_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setDifficultyLevel(opt.value)}
                  className={`flex-1 rounded border px-3 py-2 text-sm font-medium transition-colors ${
                    difficultyLevel === opt.value
                      ? 'border-zinc-900 bg-zinc-900 text-white'
                      : 'border-zinc-300 text-zinc-600 hover:border-zinc-400'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 질문 유형 */}
          <div>
            <label className="block text-xs text-zinc-500 mb-2">
              질문 유형 <span className="text-red-500">*</span>
              <span className="ml-1 text-zinc-400">(1개 이상 선택)</span>
            </label>
            <div className="flex flex-col gap-2">
              {QUESTION_TYPE_OPTIONS.map((opt) => (
                <label
                  key={opt.value}
                  className={`flex cursor-pointer items-center gap-3 rounded border px-4 py-3 transition-colors ${
                    selectedTypes.has(opt.value)
                      ? 'border-zinc-900 bg-zinc-50'
                      : 'border-zinc-200 hover:border-zinc-300'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={selectedTypes.has(opt.value)}
                    onChange={() => toggleQuestionType(opt.value)}
                    className="h-4 w-4 accent-zinc-800"
                  />
                  <div>
                    <span className="text-sm font-medium text-zinc-900">{opt.label}</span>
                    <span className="ml-2 text-xs text-zinc-400">{opt.description}</span>
                  </div>
                </label>
              ))}
            </div>
          </div>

          {/* 에러 메시지 */}
          {createError && (
            <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {createError}
            </div>
          )}

          {/* 생성 버튼 */}
          <button
            onClick={handleCreate}
            disabled={creating || selectedTypes.size === 0}
            className="w-full rounded bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {creating ? 'AI 질문 생성 중... (최대 30초 소요)' : `AI 면접 질문 ${questionCount}개 생성`}
          </button>

          <p className="text-xs text-zinc-400 text-center">
            포트폴리오와 자소서 내용을 기반으로 AI가 맞춤 면접 질문을 생성합니다.
          </p>
        </div>
      </section>

      {/* 하단 네비게이션 */}
      <div className="mt-10 flex justify-between text-sm">
        <Link
          href={`/applications/${applicationId}/generate`}
          className="text-zinc-500 hover:text-zinc-700"
        >
          ← 자소서 생성
        </Link>
        <Link href="/interview/history" className="text-zinc-500 hover:text-zinc-700">
          면접 이력 →
        </Link>
      </div>
    </main>
  );
}
