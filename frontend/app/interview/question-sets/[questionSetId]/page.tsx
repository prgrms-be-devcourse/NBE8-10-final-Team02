'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  addManualQuestion,
  deleteQuestion,
  getQuestionSetDetail,
  InterviewApiError,
  startSession,
} from '@/api/interview';
import type {
  InterviewDifficultyLevel,
  InterviewManualQuestionCreateRequest,
  InterviewQuestionSetDetail,
} from '@/types/interview';

const MANUAL_QUESTION_TYPE_OPTIONS: Array<{
  value: InterviewManualQuestionCreateRequest['questionType'];
  label: string;
}> = [
  { value: 'experience', label: '경험' },
  { value: 'project', label: '프로젝트' },
  { value: 'technical_cs', label: 'CS' },
  { value: 'technical_stack', label: '기술 스택' },
  { value: 'behavioral', label: '행동' },
];

const DIFFICULTY_OPTIONS: Array<{ value: InterviewDifficultyLevel; label: string }> = [
  { value: 'easy', label: '쉬움' },
  { value: 'medium', label: '보통' },
  { value: 'hard', label: '어려움' },
];

const QUESTION_TYPE_LABEL: Record<string, string> = {
  experience: '경험',
  project: '프로젝트',
  technical_cs: 'CS',
  technical_stack: '기술 스택',
  behavioral: '행동',
  follow_up: '꼬리 질문',
};

const DIFFICULTY_LABEL: Record<InterviewDifficultyLevel, string> = {
  easy: '쉬움',
  medium: '보통',
  hard: '어려움',
};

export default function QuestionSetDetailPage() {
  const params = useParams();
  const router = useRouter();
  const questionSetId = Number(params.questionSetId);

  const [questionSet, setQuestionSet] = useState<InterviewQuestionSetDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [manualQuestionText, setManualQuestionText] = useState('');
  const [manualQuestionType, setManualQuestionType] =
    useState<InterviewManualQuestionCreateRequest['questionType']>('behavioral');
  const [manualDifficultyLevel, setManualDifficultyLevel] =
    useState<InterviewDifficultyLevel>('medium');
  const [editError, setEditError] = useState<string | null>(null);
  const [editSuccess, setEditSuccess] = useState<string | null>(null);
  const [addingQuestion, setAddingQuestion] = useState(false);
  const [deletingQuestionId, setDeletingQuestionId] = useState<number | null>(null);

  const [startingSession, setStartingSession] = useState(false);
  const [sessionStartError, setSessionStartError] = useState<string | null>(null);

  const questionCountValidForSession = useMemo(() => {
    if (!questionSet) {
      return false;
    }

    return questionSet.questionCount >= 3 && questionSet.questionCount <= 20;
  }, [questionSet]);

  const loadQuestionSet = useCallback(async () => {
    if (!Number.isFinite(questionSetId)) {
      setLoadError('올바른 질문 세트 경로가 아닙니다.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setLoadError(null);

    try {
      const data = await getQuestionSetDetail(questionSetId);
      setQuestionSet(data);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '질문 세트를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [questionSetId]);

  useEffect(() => {
    void loadQuestionSet();
  }, [loadQuestionSet]);

  function handleInterviewApiError(err: unknown, fallbackMessage: string) {
    if (err instanceof InterviewApiError) {
      const fieldHint = err.fieldErrors[0];
      return fieldHint ? `${err.message} (${fieldHint.field})` : err.message;
    }

    return err instanceof Error ? err.message : fallbackMessage;
  }

  async function handleManualQuestionSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!manualQuestionText.trim()) {
      setEditError('질문 내용을 입력해주세요.');
      setEditSuccess(null);
      return;
    }

    setAddingQuestion(true);
    setEditError(null);
    setEditSuccess(null);

    try {
      await addManualQuestion(questionSetId, {
        questionText: manualQuestionText.trim(),
        questionType: manualQuestionType,
        difficultyLevel: manualDifficultyLevel,
      });
      setManualQuestionText('');
      setManualQuestionType('behavioral');
      setManualDifficultyLevel('medium');
      setEditSuccess('질문이 추가되었습니다.');
      await loadQuestionSet();
    } catch (err) {
      setEditError(handleInterviewApiError(err, '질문을 추가하지 못했습니다.'));
    } finally {
      setAddingQuestion(false);
    }
  }

  async function handleDeleteQuestion(questionId: number) {
    setDeletingQuestionId(questionId);
    setEditError(null);
    setEditSuccess(null);

    try {
      await deleteQuestion(questionSetId, questionId);
      setEditSuccess('질문을 삭제했습니다.');
      await loadQuestionSet();
    } catch (err) {
      setEditError(handleInterviewApiError(err, '질문을 삭제하지 못했습니다.'));
    } finally {
      setDeletingQuestionId(null);
    }
  }

  async function handleStartSession() {
    setStartingSession(true);
    setSessionStartError(null);

    try {
      const session = await startSession(questionSetId);
      router.push(`/interview/sessions/${session.id}`);
    } catch (err) {
      setSessionStartError(handleInterviewApiError(err, '면접 세션을 시작하지 못했습니다.'));
    } finally {
      setStartingSession(false);
    }
  }

  if (loading) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-12">
        <p className="text-sm text-zinc-400">질문 세트를 불러오는 중...</p>
      </main>
    );
  }

  if (loadError || !questionSet) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-12">
        <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {loadError ?? '질문 세트를 찾을 수 없습니다.'}
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

  return (
    <main className="mx-auto max-w-5xl px-4 py-10">
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link
            href={`/applications/${questionSet.applicationId}/question-sets/new`}
            className="text-xs text-zinc-400 hover:text-zinc-600"
          >
            ← 질문 생성으로
          </Link>
          <h1 className="mt-2 text-2xl font-semibold text-zinc-900">{questionSet.title}</h1>
          <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-zinc-500">
            <span>질문 {questionSet.questionCount}개</span>
            <span>난이도 {DIFFICULTY_LABEL[questionSet.difficultyLevel]}</span>
            <span>{new Date(questionSet.createdAt).toLocaleString('ko-KR')}</span>
          </div>
        </div>

        <div className="flex flex-wrap gap-3">
          <Link
            href={`/applications/${questionSet.applicationId}`}
            className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700"
          >
            지원 준비 상세
          </Link>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <section className="rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-medium text-zinc-500">질문 리스트</p>
              <h2 className="mt-1 text-lg font-semibold text-zinc-900">세션 시작 전 최종 검토</h2>
            </div>
            <button
              type="button"
              onClick={() => void loadQuestionSet()}
              className="text-xs font-medium text-zinc-500 underline"
            >
              새로고침
            </button>
          </div>

          <div className="space-y-3">
            {questionSet.questions.map((question) => (
              <article
                key={question.id}
                className="rounded-2xl border border-zinc-200 px-4 py-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
                      Q{question.questionOrder}
                    </span>
                    <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                      {QUESTION_TYPE_LABEL[question.questionType] ?? question.questionType}
                    </span>
                    <span className="rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">
                      {DIFFICULTY_LABEL[question.difficultyLevel]}
                    </span>
                    {question.parentQuestionId && (
                      <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">
                        꼬리 질문
                      </span>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={() => handleDeleteQuestion(question.id)}
                    disabled={deletingQuestionId === question.id}
                    className="text-xs font-medium text-zinc-400 underline hover:text-red-600 disabled:no-underline disabled:opacity-50"
                  >
                    {deletingQuestionId === question.id ? '삭제 중...' : '삭제'}
                  </button>
                </div>

                <p className="mt-3 text-sm leading-6 text-zinc-800">{question.questionText}</p>
              </article>
            ))}
          </div>
        </section>

        <aside className="space-y-6">
          <section className="rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
            <p className="text-xs font-medium text-zinc-500">세트 상태</p>
            <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
              <div className="rounded-xl bg-zinc-50 px-3 py-3">
                <dt className="text-zinc-500">질문 수</dt>
                <dd className="mt-1 font-medium text-zinc-900">{questionSet.questionCount}개</dd>
              </div>
              <div className="rounded-xl bg-zinc-50 px-3 py-3">
                <dt className="text-zinc-500">세션 시작</dt>
                <dd className="mt-1 font-medium text-zinc-900">
                  {questionCountValidForSession ? '가능' : '보완 필요'}
                </dd>
              </div>
            </dl>

            <p className="mt-4 text-xs leading-5 text-zinc-500">
              질문 순서는 서버가 관리합니다. 질문 추가/삭제 후 새로고침하면 최신 순서대로 다시 표시됩니다.
            </p>

            {!questionCountValidForSession && (
              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                세션 시작은 질문 3개 이상 20개 이하일 때만 가능합니다. 현재 질문 수를 먼저 조정해주세요.
              </div>
            )}

            {sessionStartError && (
              <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {sessionStartError}
              </div>
            )}

            <button
              type="button"
              onClick={handleStartSession}
              disabled={startingSession || !questionCountValidForSession}
              className="mt-4 w-full rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              {startingSession ? '세션 시작 중...' : '모의 면접 시작'}
            </button>
          </section>

          <section className="rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
            <p className="text-xs font-medium text-zinc-500">수동 질문 추가</p>
            <form onSubmit={handleManualQuestionSubmit} className="mt-4 space-y-4">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-zinc-700">질문 내용</label>
                <textarea
                  rows={4}
                  value={manualQuestionText}
                  onChange={(event) => setManualQuestionText(event.target.value)}
                  placeholder="직접 추가할 질문을 입력하세요"
                  className="w-full rounded-xl border border-zinc-300 px-3 py-2.5 text-sm focus:border-zinc-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-zinc-700">질문 유형</label>
                <select
                  value={manualQuestionType}
                  onChange={(event) =>
                    setManualQuestionType(event.target.value as InterviewManualQuestionCreateRequest['questionType'])
                  }
                  className="w-full rounded-xl border border-zinc-300 px-3 py-2.5 text-sm focus:border-zinc-500 focus:outline-none"
                >
                  {MANUAL_QUESTION_TYPE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-medium text-zinc-700">난이도</label>
                <select
                  value={manualDifficultyLevel}
                  onChange={(event) =>
                    setManualDifficultyLevel(event.target.value as InterviewDifficultyLevel)
                  }
                  className="w-full rounded-xl border border-zinc-300 px-3 py-2.5 text-sm focus:border-zinc-500 focus:outline-none"
                >
                  {DIFFICULTY_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>

              {editError && (
                <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {editError}
                </div>
              )}

              {editSuccess && (
                <div className="rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
                  {editSuccess}
                </div>
              )}

              <button
                type="submit"
                disabled={addingQuestion}
                className="w-full rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
              >
                {addingQuestion ? '추가 중...' : '질문 추가'}
              </button>
            </form>
          </section>
        </aside>
      </div>
    </main>
  );
}
