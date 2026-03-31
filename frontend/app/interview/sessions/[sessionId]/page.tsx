'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import {
  completeSession,
  getSessionDetail,
  InterviewApiError,
  pauseSession,
  resumeSession,
  submitSessionAnswer,
} from '@/api/interview';
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
const AUTO_PAUSE_THRESHOLD_MINUTES = 30;
const AUTO_PAUSE_THRESHOLD_MS = AUTO_PAUSE_THRESHOLD_MINUTES * 60 * 1000;

function formatDateTime(value: string | null) {
  if (!value) {
    return '기록 없음';
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

function getElapsedSince(value: string | null) {
  if (!value) {
    return null;
  }

  const elapsed = Date.now() - new Date(value).getTime();
  return Number.isFinite(elapsed) ? Math.max(elapsed, 0) : null;
}

function isAutoPauseLikely(session: InterviewSessionDetail) {
  if (session.status !== 'paused') {
    return false;
  }

  const elapsed = getElapsedSince(session.lastActivityAt);
  return elapsed !== null && elapsed >= AUTO_PAUSE_THRESHOLD_MS;
}

export default function InterviewSessionPage() {
  const params = useParams();
  const router = useRouter();
  const sessionId = Number(params.sessionId);

  const [session, setSession] = useState<InterviewSessionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [answerText, setAnswerText] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState<string | null>(null);
  const [submittingMode, setSubmittingMode] = useState<'answer' | 'skip' | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);
  const [transitionSuccess, setTransitionSuccess] = useState<string | null>(null);
  const [transitionMode, setTransitionMode] = useState<'pause' | 'resume' | null>(null);
  const [completeError, setCompleteError] = useState<string | null>(null);
  const [completingSession, setCompletingSession] = useState(false);

  const loadSession = useCallback(async (options?: {
    resetAnswerText?: boolean;
    showPageLoading?: boolean;
  }) => {
    if (!Number.isFinite(sessionId)) {
      setError('올바른 세션 경로가 아닙니다.');
      setLoading(false);
      return null;
    }

    const showPageLoading = options?.showPageLoading ?? true;

    if (showPageLoading) {
      setLoading(true);
    }
    setError(null);

    try {
      const data = await getSessionDetail(sessionId);
      setSession(data);
      if (options?.resetAnswerText) {
        setAnswerText('');
      }
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : '세션 정보를 불러오지 못했습니다.');
      return null;
    } finally {
      if (showPageLoading) {
        setLoading(false);
      }
    }
  }, [sessionId]);

  useEffect(() => {
    void loadSession();
  }, [loadSession]);

  function formatApiError(err: unknown, fallbackMessage: string) {
    if (err instanceof InterviewApiError) {
      const fieldHint = err.fieldErrors[0];
      return fieldHint ? `${err.message} (${fieldHint.field})` : err.message;
    }

    return err instanceof Error ? err.message : fallbackMessage;
  }

  function buildAutoPauseNotice(nextSession: InterviewSessionDetail) {
    if (isAutoPauseLikely(nextSession)) {
      return `마지막 활동 후 ${AUTO_PAUSE_THRESHOLD_MINUTES}분 이상 지나 세션이 자동 일시정지된 것으로 보입니다. 재개 후 다시 이어서 진행해주세요.`;
    }

    if (nextSession.status === 'paused') {
      return '세션이 현재 일시정지 상태입니다. 재개 후 다시 이어서 진행해주세요.';
    }

    if (nextSession.status === 'completed' || nextSession.status === 'feedback_completed') {
      return '이미 종료된 면접 세션입니다.';
    }

    return null;
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
    setTransitionError(null);
    setTransitionSuccess(null);
    setCompleteError(null);

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
      await loadSession({ resetAnswerText: true, showPageLoading: false });
    } catch (err) {
      if (err instanceof InterviewApiError && err.code === 'INTERVIEW_SESSION_STATUS_CONFLICT') {
        const refreshed = await loadSession({ showPageLoading: false });
        const autoPauseNotice = refreshed ? buildAutoPauseNotice(refreshed) : null;
        setSubmitError(autoPauseNotice ?? formatApiError(err, '답변을 저장하지 못했습니다.'));
      } else {
        setSubmitError(formatApiError(err, '답변을 저장하지 못했습니다.'));
      }
    } finally {
      setSubmittingMode(null);
    }
  }

  async function handleTransition(action: 'pause' | 'resume') {
    if (!session) {
      return;
    }

    setTransitionMode(action);
    setTransitionError(null);
    setTransitionSuccess(null);
    setSubmitError(null);
    setSubmitSuccess(null);
    setCompleteError(null);

    try {
      const result = action === 'pause'
        ? await pauseSession(session.id)
        : await resumeSession(session.id);

      setTransitionSuccess(
        result.status === 'paused'
          ? '세션을 일시정지했습니다. 같은 세션에서 다시 재개할 수 있습니다.'
          : '세션을 재개했습니다. 이어서 답변을 제출할 수 있습니다.',
      );
      await loadSession({ showPageLoading: false });
    } catch (err) {
      if (err instanceof InterviewApiError && err.code === 'INTERVIEW_SESSION_STATUS_CONFLICT') {
        const refreshed = await loadSession({ showPageLoading: false });
        const autoPauseNotice = refreshed ? buildAutoPauseNotice(refreshed) : null;
        setTransitionError(autoPauseNotice ?? formatApiError(err, '세션 상태를 변경하지 못했습니다.'));
      } else {
        setTransitionError(formatApiError(err, '세션 상태를 변경하지 못했습니다.'));
      }
    } finally {
      setTransitionMode(null);
    }
  }

  async function handleCompleteSession() {
    if (!session) {
      return;
    }

    setCompletingSession(true);
    setCompleteError(null);
    setSubmitError(null);
    setSubmitSuccess(null);
    setTransitionError(null);
    setTransitionSuccess(null);

    try {
      await completeSession(session.id);
      router.push(`/interview/sessions/${session.id}/result`);
    } catch (err) {
      if (
        err instanceof InterviewApiError &&
        (err.retryable || err.code === 'INTERVIEW_SESSION_ALREADY_COMPLETED')
      ) {
        router.push(`/interview/sessions/${session.id}/result`);
        return;
      }

      if (err instanceof InterviewApiError && err.code === 'INTERVIEW_SESSION_STATUS_CONFLICT') {
        await loadSession({ showPageLoading: false });
      }

      setCompleteError(formatApiError(err, '세션을 종료하지 못했습니다.'));
    } finally {
      setCompletingSession(false);
    }
  }

  const answerLength = answerText.trim().length;
  const actionBusy = submittingMode !== null || transitionMode !== null || completingSession;
  const autoPauseLikely = session ? isAutoPauseLikely(session) : false;
  const canSubmitAnswer =
    session?.status === 'in_progress' &&
    !!session.currentQuestion &&
    answerLength >= MIN_ANSWER_LENGTH &&
    answerLength <= MAX_ANSWER_LENGTH &&
    !actionBusy;
  const canSkip =
    session?.status === 'in_progress' && !!session.currentQuestion && !actionBusy;
  const canPause = session?.status === 'in_progress' && !actionBusy;
  const canResume = session?.status === 'paused' && !actionBusy;
  const canComplete =
    !!session &&
    session.remainingQuestionCount === 0 &&
    (session.status === 'in_progress' || session.status === 'paused') &&
    !actionBusy;

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
          답변 제출, 명시적 일시정지/재개, 자동 일시정지 복원 안내와 결과 이동을 현재 화면에서 처리합니다.
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

        {session.status === 'paused' && (
          <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-4">
            <p className="text-sm font-semibold text-amber-900">
              {autoPauseLikely ? '자동 일시정지된 것으로 보입니다.' : '현재 일시정지된 세션입니다.'}
            </p>
            <p className="mt-2 text-sm leading-6 text-amber-800">
              {autoPauseLikely
                ? `마지막 활동 후 ${AUTO_PAUSE_THRESHOLD_MINUTES}분 이상 지나 현재 세션이 일시정지 상태로 보입니다. 재개 후 같은 질문부터 이어서 답변을 제출하세요.`
                : '같은 세션에서 그대로 재개할 수 있습니다. 위의 `재개` 버튼으로 진행을 이어가세요.'}
            </p>
            <p className="mt-2 text-sm text-amber-800">
              {session.resumeAvailable
                ? `새로고침이나 브라우저 이탈 후에도 ${AUTO_PAUSE_THRESHOLD_MINUTES}분 이내에는 같은 세션으로 복원할 수 있습니다.`
                : '현재는 복원 가능 여부를 다시 확인할 수 없습니다. 세션 상태를 새로 불러온 뒤 재개를 시도하세요.'}
            </p>
          </div>
        )}

        {session.status !== 'paused' && session.resumeAvailable && (
          <div className="mt-5 rounded-2xl border border-blue-200 bg-blue-50 px-4 py-4 text-sm text-blue-900">
            새로고침이나 브라우저 이탈 후에도 {AUTO_PAUSE_THRESHOLD_MINUTES}분 이내에는 같은 세션으로 다시 돌아올 수 있습니다.
          </div>
        )}

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

        <div className="mt-5 grid gap-3 md:grid-cols-3">
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
          <div className="rounded-xl bg-zinc-50 px-4 py-3">
            <p className="text-xs text-zinc-500">마지막 활동 시각</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">
              {formatDateTime(session.lastActivityAt)}
            </p>
          </div>
        </div>

        <div className="mt-5 rounded-2xl border border-zinc-200 px-4 py-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-xs font-medium text-zinc-500">세션 종료와 결과</p>
              <p className="mt-1 text-sm text-zinc-600">
                모든 질문에 답변한 뒤 세션을 종료하면 결과 리포트 화면으로 이동합니다.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              {(session.status === 'completed' || session.status === 'feedback_completed') && (
                <Link
                  href={`/interview/sessions/${session.id}/result`}
                  className="rounded-full border border-blue-300 bg-blue-50 px-4 py-2.5 text-sm font-medium text-blue-800"
                >
                  결과 확인
                </Link>
              )}
              {(session.status === 'in_progress' || session.status === 'paused') && (
                <button
                  type="button"
                  onClick={() => void handleCompleteSession()}
                  disabled={!canComplete}
                  className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {completingSession ? '세션 종료 중...' : '세션 종료'}
                </button>
              )}
            </div>
          </div>

          {session.remainingQuestionCount > 0 &&
            (session.status === 'in_progress' || session.status === 'paused') && (
              <p className="mt-3 text-sm text-amber-700">
                남은 질문이 {session.remainingQuestionCount}개라서 아직 세션을 종료할 수 없습니다.
              </p>
            )}

          {session.status === 'completed' && (
            <p className="mt-3 text-sm text-zinc-600">
              세션은 종료됐지만 결과가 아직 준비 중일 수 있습니다. 결과 화면에서 다시 확인할 수 있습니다.
            </p>
          )}

          {session.status === 'feedback_completed' && (
            <p className="mt-3 text-sm text-blue-700">
              결과 리포트가 준비되었습니다. `결과 확인` 버튼으로 이동하세요.
            </p>
          )}

          {completeError && (
            <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {completeError}
            </div>
          )}
        </div>

        <div className="mt-5 rounded-2xl border border-zinc-200 px-4 py-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-xs font-medium text-zinc-500">세션 상태 전환</p>
              <p className="mt-1 text-sm text-zinc-600">
                진행 중에는 `일시정지`, 일시정지 상태에서는 `재개`만 허용합니다.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              {session.status === 'in_progress' && (
                <button
                  type="button"
                  onClick={() => void handleTransition('pause')}
                  disabled={!canPause}
                  className="rounded-full border border-amber-300 bg-amber-50 px-4 py-2.5 text-sm font-medium text-amber-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {transitionMode === 'pause' ? '일시정지 중...' : '일시정지'}
                </button>
              )}
              {session.status === 'paused' && (
                <button
                  type="button"
                  onClick={() => void handleTransition('resume')}
                  disabled={!canResume}
                  className="rounded-full border border-green-300 bg-green-50 px-4 py-2.5 text-sm font-medium text-green-800 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {transitionMode === 'resume' ? '재개 중...' : '재개'}
                </button>
              )}
            </div>
          </div>

          {transitionError && (
            <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {transitionError}
            </div>
          )}

          {transitionSuccess && (
            <div className="mt-4 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
              {transitionSuccess}
            </div>
          )}
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
            disabled={session.status !== 'in_progress' || !session.currentQuestion || actionBusy}
            placeholder={
              session.status === 'paused'
                ? '일시정지된 세션은 재개 후 답변을 제출할 수 있습니다.'
                : session.status !== 'in_progress'
                  ? '현재 상태에서는 답변을 제출할 수 없습니다.'
                : '면접 답변을 입력하세요'
            }
            className="mt-4 w-full rounded-2xl border border-zinc-300 px-4 py-3 text-sm leading-6 text-zinc-900 focus:border-zinc-500 focus:outline-none disabled:cursor-not-allowed disabled:bg-zinc-50 disabled:text-zinc-400"
          />

          {session.status === 'paused' && (
            <p className="mt-3 text-sm text-amber-700">
              {autoPauseLikely
                ? `마지막 활동 시각 기준으로 자동 일시정지된 것으로 보입니다. 재개 후 답변을 다시 제출해주세요.`
                : '이 세션은 현재 일시정지 상태입니다. 위의 `재개` 버튼으로 다시 진행할 수 있습니다.'}
            </p>
          )}

          {session.status === 'completed' && (
            <p className="mt-3 text-sm text-zinc-600">
              이 세션은 종료되었습니다. 위의 `결과 확인` 버튼에서 결과 리포트를 다시 확인할 수 있습니다.
            </p>
          )}

          {session.status === 'feedback_completed' && (
            <p className="mt-3 text-sm text-zinc-600">
              피드백이 완료된 세션입니다. 추가 답변은 제출할 수 없고 결과 리포트만 확인할 수 있습니다.
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
