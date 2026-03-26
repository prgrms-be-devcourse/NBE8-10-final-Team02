'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { getSessionDetail, InterviewApiError, submitSessionAnswer } from '@/api/interview';
import type { InterviewSessionDetail, InterviewSessionStatus } from '@/types/interview';

const STATUS_LABEL: Record<InterviewSessionStatus, string> = {
  ready: '준비',
  in_progress: '진행 중',
  paused: '일시정지',
  completed: '종료',
  feedback_completed: '피드백 완료',
};

const STATUS_TONE: Record<InterviewSessionStatus, string> = {
  ready: 'bg-zinc-100 text-zinc-700',
  in_progress: 'bg-green-50 text-green-700',
  paused: 'bg-amber-50 text-amber-700',
  completed: 'bg-zinc-100 text-zinc-700',
  feedback_completed: 'bg-blue-50 text-blue-700',
};

const QUESTION_TYPE_LABEL: Record<string, string> = {
  experience: '경험',
  project: '프로젝트',
  technical_cs: 'CS',
  technical_stack: '기술 스택',
  behavioral: '행동',
  follow_up: '꼬리 질문',
};

const DIFFICULTY_LABEL: Record<string, string> = {
  easy: '쉬움',
  medium: '보통',
  hard: '어려움',
};

const MIN_ANSWER_LENGTH = 50;
const MAX_ANSWER_LENGTH = 1000;

export default function InterviewSessionPage() {
  const params = useParams();
  const sessionId = Number(params.sessionId);

  const [session, setSession] = useState<InterviewSessionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [answerText, setAnswerText] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState<string | null>(null);
  const [submittingMode, setSubmittingMode] = useState<'answer' | 'skip' | null>(null);

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
      setAnswerText('');
    } catch (err) {
      setError(err instanceof Error ? err.message : '세션 정보를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    void loadSession();
  }, [loadSession]);

  function formatSubmitError(err: unknown) {
    if (err instanceof InterviewApiError) {
      const fieldHint = err.fieldErrors[0];
      return fieldHint ? `${err.message} (${fieldHint.field})` : err.message;
    }

    return err instanceof Error ? err.message : '답변을 저장하지 못했습니다.';
  }

  async function handleSubmitAnswer(isSkipped: boolean) {
    if (!session?.currentQuestion) {
      setSubmitError('현재 제출 가능한 질문이 없습니다.');
      setSubmitSuccess(null);
      return;
    }

    if (!isSkipped) {
      const trimmed = answerText.trim();
      if (trimmed.length < MIN_ANSWER_LENGTH) {
        setSubmitError(`답변은 ${MIN_ANSWER_LENGTH}자 이상 입력해주세요.`);
        setSubmitSuccess(null);
        return;
      }

      if (trimmed.length > MAX_ANSWER_LENGTH) {
        setSubmitError(`답변은 ${MAX_ANSWER_LENGTH}자 이하로 입력해주세요.`);
        setSubmitSuccess(null);
        return;
      }
    }

    setSubmittingMode(isSkipped ? 'skip' : 'answer');
    setSubmitError(null);
    setSubmitSuccess(null);

    try {
      const result = await submitSessionAnswer(session.id, {
        questionId: session.currentQuestion.id,
        answerOrder: session.currentQuestion.questionOrder,
        isSkipped,
        ...(isSkipped ? {} : { answerText: answerText.trim() }),
      });

      setSubmitSuccess(
        result.isSkipped
          ? `Q${result.answerOrder}를 건너뛰었습니다. 다음 질문을 불러옵니다.`
          : `Q${result.answerOrder} 답변을 저장했습니다. 다음 질문을 불러옵니다.`,
      );
      await loadSession();
    } catch (err) {
      setSubmitError(formatSubmitError(err));
    } finally {
      setSubmittingMode(null);
    }
  }

  const answerLength = answerText.trim().length;
  const canSubmitAnswer =
    session?.status === 'in_progress' &&
    !!session.currentQuestion &&
    answerLength >= MIN_ANSWER_LENGTH &&
    answerLength <= MAX_ANSWER_LENGTH &&
    submittingMode === null;
  const canSkip =
    session?.status === 'in_progress' && !!session.currentQuestion && submittingMode === null;

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
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_TONE[session.status]}`}
          >
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
              <span>{QUESTION_TYPE_LABEL[session.currentQuestion.questionType] ?? session.currentQuestion.questionType}</span>
              <span>{DIFFICULTY_LABEL[session.currentQuestion.difficultyLevel] ?? session.currentQuestion.difficultyLevel}</span>
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

        <div className="mt-6 rounded-2xl border border-zinc-200 px-4 py-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-medium text-zinc-500">답변 입력</p>
              <p className="mt-1 text-sm text-zinc-600">
                일반 답변은 {MIN_ANSWER_LENGTH}자 이상 {MAX_ANSWER_LENGTH}자 이하로 입력합니다.
              </p>
            </div>
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                answerLength > MAX_ANSWER_LENGTH
                  ? 'bg-red-50 text-red-700'
                  : answerLength >= MIN_ANSWER_LENGTH
                    ? 'bg-green-50 text-green-700'
                    : 'bg-zinc-100 text-zinc-600'
              }`}
            >
              {answerLength}/{MAX_ANSWER_LENGTH}
            </span>
          </div>

          <textarea
            rows={8}
            value={answerText}
            onChange={(event) => setAnswerText(event.target.value)}
            disabled={session.status !== 'in_progress' || !session.currentQuestion || submittingMode !== null}
            placeholder={
              session.status !== 'in_progress'
                ? '현재 상태에서는 답변을 제출할 수 없습니다.'
                : '면접 답변을 입력하세요'
            }
            className="mt-4 w-full rounded-2xl border border-zinc-300 px-4 py-3 text-sm leading-6 text-zinc-900 focus:border-zinc-500 focus:outline-none disabled:cursor-not-allowed disabled:bg-zinc-50 disabled:text-zinc-400"
          />

          {session.status === 'paused' && (
            <p className="mt-3 text-sm text-amber-700">
              이 세션은 현재 일시정지 상태입니다. 재개 UI는 다음 작업에서 연결합니다.
            </p>
          )}

          {session.status === 'completed' && (
            <p className="mt-3 text-sm text-zinc-600">
              이 세션은 종료되었습니다. 결과 화면 연결은 다음 단계에서 이어집니다.
            </p>
          )}

          {session.status === 'feedback_completed' && (
            <p className="mt-3 text-sm text-zinc-600">
              피드백이 완료된 세션입니다. 추가 답변은 제출할 수 없습니다.
            </p>
          )}

          {submitError && (
            <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {submitError}
            </div>
          )}

          {submitSuccess && (
            <div className="mt-4 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
              {submitSuccess}
            </div>
          )}

          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => void handleSubmitAnswer(false)}
              disabled={!canSubmitAnswer}
              className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submittingMode === 'answer' ? '제출 중...' : '제출'}
            </button>
            <button
              type="button"
              onClick={() => void handleSubmitAnswer(true)}
              disabled={!canSkip}
              className="rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-medium text-zinc-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submittingMode === 'skip' ? '건너뛰는 중...' : '건너뛰기'}
            </button>
          </div>
        </div>
      </section>
    </main>
  );
}
