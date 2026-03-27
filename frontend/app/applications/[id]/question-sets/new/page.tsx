'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getApplication } from '@/api/application';
import { createQuestionSet, InterviewApiError } from '@/api/interview';
import type { Application } from '@/types/application';
import type {
  InterviewDifficultyLevel,
  InterviewQuestionType,
} from '@/types/interview';

const QUESTION_TYPE_OPTIONS: Array<{ value: InterviewQuestionType; label: string; description: string }> = [
  { value: 'experience', label: '경험', description: '지원 동기, 성장 과정, 문제 해결 경험 중심' },
  { value: 'project', label: '프로젝트', description: '프로젝트 설계와 협업 경험 중심' },
  { value: 'technical_cs', label: 'CS', description: '기초 CS와 이론 확인' },
  { value: 'technical_stack', label: '기술 스택', description: '사용 기술 선택 이유와 적용 경험' },
  { value: 'behavioral', label: '행동', description: '협업, 갈등 해결, 의사소통 확인' },
  { value: 'follow_up', label: '꼬리 질문', description: '답변을 더 깊게 파고드는 추가 질문' },
];

const DIFFICULTY_OPTIONS: Array<{ value: InterviewDifficultyLevel; label: string; description: string }> = [
  { value: 'easy', label: '쉬움', description: '핵심 개념과 경험을 먼저 점검' },
  { value: 'medium', label: '보통', description: '실전 면접 기준의 기본 난이도' },
  { value: 'hard', label: '어려움', description: '압박 질문과 깊은 기술 질문 포함' },
];

function getDefaultTitle(application: Application) {
  if (application.companyName) {
    return `${application.companyName} ${application.jobRole} 예상 질문 세트`;
  }

  return `${application.jobRole} 예상 질문 세트`;
}

export default function NewQuestionSetPage() {
  const params = useParams();
  const router = useRouter();
  const applicationId = Number(params.id);

  const [application, setApplication] = useState<Application | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [title, setTitle] = useState('');
  const [questionCount, setQuestionCount] = useState('5');
  const [difficultyLevel, setDifficultyLevel] = useState<InterviewDifficultyLevel>('medium');
  const [questionTypes, setQuestionTypes] = useState<InterviewQuestionType[]>(
    QUESTION_TYPE_OPTIONS.map((option) => option.value),
  );

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const applicationSummary = useMemo(() => {
    if (!application) {
      return null;
    }

    return {
      title: application.applicationTitle || application.jobRole,
      companyName: application.companyName,
      jobRole: application.jobRole,
      applicationType: application.applicationType,
      status: application.status,
    };
  }, [application]);

  const loadApplication = useCallback(async () => {
    if (!Number.isFinite(applicationId)) {
      setLoadError('올바른 지원 준비 경로가 아닙니다.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setLoadError(null);

    try {
      const data = await getApplication(applicationId);
      setApplication(data);
      setTitle(getDefaultTitle(data));
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '지원 준비를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [applicationId]);

  useEffect(() => {
    void loadApplication();
  }, [loadApplication]);

  function toggleQuestionType(type: InterviewQuestionType) {
    setQuestionTypes((prev) =>
      prev.includes(type) ? prev.filter((value) => value !== type) : [...prev, type],
    );
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsedQuestionCount = Number(questionCount);
    if (!Number.isFinite(parsedQuestionCount) || parsedQuestionCount < 1 || parsedQuestionCount > 20) {
      setSubmitError('질문 수는 1개 이상 20개 이하로 입력해주세요.');
      return;
    }

    if (questionTypes.length === 0) {
      setSubmitError('질문 카테고리를 하나 이상 선택해주세요.');
      return;
    }

    setSubmitting(true);
    setSubmitError(null);

    try {
      const created = await createQuestionSet({
        applicationId,
        title: title.trim() || undefined,
        questionCount: parsedQuestionCount,
        difficultyLevel,
        questionTypes,
      });
      router.push(`/interview/question-sets/${created.questionSetId}`);
    } catch (err) {
      if (err instanceof InterviewApiError) {
        const fieldHint = err.fieldErrors[0];
        setSubmitError(fieldHint ? `${err.message} (${fieldHint.field})` : err.message);
      } else {
        setSubmitError(err instanceof Error ? err.message : '질문 세트를 생성하지 못했습니다.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-12">
        <p className="text-sm text-zinc-400">지원 준비 정보를 불러오는 중...</p>
      </main>
    );
  }

  if (loadError || !applicationSummary) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-12">
        <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {loadError ?? '지원 준비를 찾을 수 없습니다.'}
        </div>
        <Link
          href="/applications"
          className="mt-4 inline-block text-sm font-medium text-zinc-500 underline"
        >
          지원 준비 목록으로
        </Link>
      </main>
    );
  }

  if (applicationSummary.status !== 'ready') {
    return (
      <main className="mx-auto max-w-3xl px-4 py-10">
        <Link
          href={`/applications/${applicationId}`}
          className="text-xs text-zinc-400 hover:text-zinc-600"
        >
          ← 지원 준비 상세로
        </Link>

        <section className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 px-6 py-6">
          <p className="text-xs font-medium text-amber-700">면접 질문 생성 전 확인</p>
          <h1 className="mt-2 text-xl font-semibold text-zinc-900">아직 질문 세트를 만들 수 없습니다.</h1>
          <p className="mt-3 text-sm text-zinc-700">
            현재 지원 준비 상태는 <span className="font-medium">작성 중</span>입니다. 자소서 답변과 연결 소스가 모두 준비되면
            질문 생성 화면을 사용할 수 있습니다.
          </p>

          <div className="mt-5 rounded-xl border border-amber-200 bg-white px-4 py-4">
            <p className="text-sm font-medium text-zinc-900">{applicationSummary.title}</p>
            <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-zinc-500">
              {applicationSummary.companyName && <span>{applicationSummary.companyName}</span>}
              <span>{applicationSummary.jobRole}</span>
              {applicationSummary.applicationType && <span>{applicationSummary.applicationType}</span>}
            </div>
          </div>

          <div className="mt-5 flex flex-wrap gap-3">
            <Link
              href={`/applications/${applicationId}/generate`}
              className="rounded-full bg-zinc-900 px-4 py-2 text-sm font-medium text-white"
            >
              자소서 생성으로 이동
            </Link>
            <Link
              href={`/applications/${applicationId}`}
              className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700"
            >
              지원 준비 상세 보기
            </Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <div className="mb-8">
        <Link
          href={`/applications/${applicationId}`}
          className="text-xs text-zinc-400 hover:text-zinc-600"
        >
          ← 지원 준비 상세로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold text-zinc-900">면접 질문 생성</h1>
        <p className="mt-2 text-sm text-zinc-500">
          자소서와 포트폴리오를 기준으로 예상 질문 세트를 생성합니다.
        </p>
      </div>

      <section className="mb-6 rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <p className="text-xs font-medium text-zinc-500">기준 지원 준비</p>
        <h2 className="mt-2 text-lg font-semibold text-zinc-900">{applicationSummary.title}</h2>
        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-zinc-500">
          {applicationSummary.companyName && <span>{applicationSummary.companyName}</span>}
          <span>{applicationSummary.jobRole}</span>
          {applicationSummary.applicationType && <span>{applicationSummary.applicationType}</span>}
          <span>상태: 준비 완료</span>
        </div>
      </section>

      <form onSubmit={handleSubmit} className="rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <div className="mb-6">
          <label className="mb-1.5 block text-sm font-medium text-zinc-700">세트 제목</label>
          <input
            type="text"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="질문 세트 제목"
            className="w-full rounded-xl border border-zinc-300 px-3 py-2.5 text-sm focus:border-zinc-500 focus:outline-none"
          />
          <p className="mt-1.5 text-xs text-zinc-400">
            비워두면 현재 지원 준비 정보를 기준으로 제목을 저장합니다.
          </p>
        </div>

        <div className="mb-6">
          <label className="mb-1.5 block text-sm font-medium text-zinc-700">질문 수</label>
          <input
            type="number"
            min={1}
            max={20}
            value={questionCount}
            onChange={(event) => setQuestionCount(event.target.value)}
            className="w-full rounded-xl border border-zinc-300 px-3 py-2.5 text-sm focus:border-zinc-500 focus:outline-none"
          />
          <p className="mt-1.5 text-xs text-zinc-400">한 번에 최대 20개까지 생성할 수 있습니다.</p>
        </div>

        <div className="mb-6">
          <p className="mb-2 text-sm font-medium text-zinc-700">난이도</p>
          <div className="grid gap-3 md:grid-cols-3">
            {DIFFICULTY_OPTIONS.map((option) => (
              <label
                key={option.value}
                className={`cursor-pointer rounded-2xl border px-4 py-4 ${
                  difficultyLevel === option.value
                    ? 'border-zinc-900 bg-zinc-900 text-white'
                    : 'border-zinc-200 bg-white text-zinc-900'
                }`}
              >
                <input
                  type="radio"
                  name="difficultyLevel"
                  value={option.value}
                  checked={difficultyLevel === option.value}
                  onChange={() => setDifficultyLevel(option.value)}
                  className="sr-only"
                />
                <p className="text-sm font-medium">{option.label}</p>
                <p
                  className={`mt-1 text-xs ${
                    difficultyLevel === option.value ? 'text-zinc-200' : 'text-zinc-500'
                  }`}
                >
                  {option.description}
                </p>
              </label>
            ))}
          </div>
        </div>

        <div className="mb-6">
          <div className="mb-2 flex items-center justify-between gap-3">
            <p className="text-sm font-medium text-zinc-700">질문 카테고리</p>
            <span className="text-xs text-zinc-400">{questionTypes.length}개 선택됨</span>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            {QUESTION_TYPE_OPTIONS.map((option) => {
              const checked = questionTypes.includes(option.value);

              return (
                <label
                  key={option.value}
                  className={`cursor-pointer rounded-2xl border px-4 py-4 ${
                    checked ? 'border-zinc-900 bg-zinc-50' : 'border-zinc-200 bg-white'
                  }`}
                >
                  <div className="flex items-start gap-3">
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleQuestionType(option.value)}
                      className="mt-0.5 h-4 w-4 accent-zinc-900"
                    />
                    <div>
                      <p className="text-sm font-medium text-zinc-900">{option.label}</p>
                      <p className="mt-1 text-xs text-zinc-500">{option.description}</p>
                    </div>
                  </div>
                </label>
              );
            })}
          </div>
        </div>

        {submitError && (
          <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {submitError}
          </div>
        )}

        <div className="flex flex-wrap gap-3">
          <button
            type="submit"
            disabled={submitting}
            className="rounded-full bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {submitting ? '질문 생성 중...' : '질문 세트 생성'}
          </button>
          <Link
            href={`/applications/${applicationId}`}
            className="rounded-full border border-zinc-300 px-5 py-2.5 text-sm font-medium text-zinc-700"
          >
            취소
          </Link>
        </div>
      </form>
    </main>
  );
}
