'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  completeSession,
  getSessionDetail,
  InterviewApiError,
  pauseSession,
  resumeSession,
  submitSessionAnswer,
} from '@/api/interview';
import type {
  InterviewQuestionType,
  InterviewSessionCurrentQuestion,
  InterviewSessionDetail,
  InterviewSessionStatus,
} from '@/types/interview';

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

const QUESTION_TYPE_LABEL: Record<InterviewQuestionType, string> = {
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
const CHAT_STORAGE_PREFIX = 'interview-session-chat';
const DRAFT_STORAGE_PREFIX = 'interview-session-draft';

type ChatMessageRole = 'question' | 'answer' | 'system';
type ChatMessageTone = 'default' | 'success' | 'warning';

interface ChatMessage {
  id: string;
  role: ChatMessageRole;
  text: string;
  tone?: ChatMessageTone;
  questionId?: number;
  questionOrder?: number;
  questionType?: InterviewQuestionType;
  difficultyLevel?: string;
  answerOrder?: number;
  isSkipped?: boolean;
  pending?: boolean;
  createdAt: string;
}

interface StoredDraft {
  questionId: number;
  text: string;
  savedAt: string;
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '기록 없음';
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

function formatTime(value: string | null) {
  if (!value) {
    return null;
  }

  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
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

function createQuestionMessage(question: InterviewSessionCurrentQuestion): ChatMessage {
  return {
    id: `question-${question.id}`,
    role: 'question',
    text: question.questionText,
    questionId: question.id,
    questionOrder: question.questionOrder,
    questionType: question.questionType,
    difficultyLevel: question.difficultyLevel,
    createdAt: new Date().toISOString(),
  };
}

function createAnswerMessage(options: {
  id: string;
  text: string;
  answerOrder: number;
  questionId: number;
  isSkipped: boolean;
  pending: boolean;
  createdAt?: string;
}): ChatMessage {
  return {
    id: options.id,
    role: 'answer',
    text: options.text,
    answerOrder: options.answerOrder,
    questionId: options.questionId,
    isSkipped: options.isSkipped,
    pending: options.pending,
    createdAt: options.createdAt ?? new Date().toISOString(),
  };
}

function createSystemMessage(text: string, tone: ChatMessageTone = 'default'): ChatMessage {
  return {
    id: `system-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role: 'system',
    text,
    tone,
    createdAt: new Date().toISOString(),
  };
}

function getChatStorageKey(sessionId: number) {
  return `${CHAT_STORAGE_PREFIX}:${sessionId}`;
}

function getDraftStorageKey(sessionId: number) {
  return `${DRAFT_STORAGE_PREFIX}:${sessionId}`;
}

function readStoredMessages(sessionId: number): ChatMessage[] {
  if (typeof window === 'undefined' || !Number.isFinite(sessionId)) {
    return [];
  }

  try {
    const raw = window.sessionStorage.getItem(getChatStorageKey(sessionId));
    if (!raw) {
      return [];
    }

    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed as ChatMessage[] : [];
  } catch {
    return [];
  }
}

function writeStoredMessages(sessionId: number, messages: ChatMessage[]) {
  if (typeof window === 'undefined' || !Number.isFinite(sessionId)) {
    return;
  }

  window.sessionStorage.setItem(getChatStorageKey(sessionId), JSON.stringify(messages));
}

function readStoredDraft(sessionId: number): StoredDraft | null {
  if (typeof window === 'undefined' || !Number.isFinite(sessionId)) {
    return null;
  }

  try {
    const raw = window.sessionStorage.getItem(getDraftStorageKey(sessionId));
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw);
    if (
      typeof parsed !== 'object' ||
      parsed === null ||
      typeof parsed.questionId !== 'number' ||
      typeof parsed.text !== 'string' ||
      typeof parsed.savedAt !== 'string'
    ) {
      return null;
    }

    return parsed as StoredDraft;
  } catch {
    return null;
  }
}

function writeStoredDraft(sessionId: number, draft: StoredDraft | null) {
  if (typeof window === 'undefined' || !Number.isFinite(sessionId)) {
    return;
  }

  if (!draft) {
    window.sessionStorage.removeItem(getDraftStorageKey(sessionId));
    return;
  }

  window.sessionStorage.setItem(getDraftStorageKey(sessionId), JSON.stringify(draft));
}

function syncMessagesWithCurrentQuestion(
  previousMessages: ChatMessage[],
  session: InterviewSessionDetail,
): ChatMessage[] {
  if (!session.currentQuestion) {
    return previousMessages;
  }

  const alreadyExists = previousMessages.some(
    (message) => message.role === 'question' && message.questionId === session.currentQuestion?.id,
  );

  if (alreadyExists) {
    return previousMessages;
  }

  return [...previousMessages, createQuestionMessage(session.currentQuestion)];
}

export default function InterviewSessionPage() {
  const params = useParams();
  const router = useRouter();
  const sessionId = Number(params.sessionId);
  const skipNextDraftPersistRef = useRef(false);

  const [session, setSession] = useState<InterviewSessionDetail | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>(() => readStoredMessages(sessionId));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [answerText, setAnswerText] = useState('');
  const [draftSavedAt, setDraftSavedAt] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submittingMode, setSubmittingMode] = useState<'answer' | 'skip' | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);
  const [transitionMode, setTransitionMode] = useState<'pause' | 'resume' | null>(null);
  const [completeError, setCompleteError] = useState<string | null>(null);
  const [completingSession, setCompletingSession] = useState(false);
  const currentQuestionId = session?.currentQuestion?.id ?? null;

  useEffect(() => {
    setMessages(readStoredMessages(sessionId));
  }, [sessionId]);

  useEffect(() => {
    writeStoredMessages(sessionId, messages);
  }, [messages, sessionId]);

  useEffect(() => {
    if (!Number.isFinite(sessionId)) {
      return;
    }

    skipNextDraftPersistRef.current = true;

    if (!currentQuestionId) {
      writeStoredDraft(sessionId, null);
      setAnswerText('');
      setDraftSavedAt(null);
      return;
    }

    const storedDraft = readStoredDraft(sessionId);
    if (!storedDraft || storedDraft.questionId !== currentQuestionId) {
      if (storedDraft) {
        writeStoredDraft(sessionId, null);
      }
      setAnswerText('');
      setDraftSavedAt(null);
      return;
    }

    setAnswerText(storedDraft.text);
    setDraftSavedAt(storedDraft.savedAt);
  }, [currentQuestionId, sessionId]);

  useEffect(() => {
    if (!Number.isFinite(sessionId) || !currentQuestionId) {
      return;
    }

    if (skipNextDraftPersistRef.current) {
      skipNextDraftPersistRef.current = false;
      return;
    }

    if (answerText.length === 0) {
      writeStoredDraft(sessionId, null);
      setDraftSavedAt(null);
      return;
    }

    const savedAt = new Date().toISOString();
    writeStoredDraft(sessionId, {
      questionId: currentQuestionId,
      text: answerText,
      savedAt,
    });
    setDraftSavedAt(savedAt);
  }, [answerText, currentQuestionId, sessionId]);

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
      setMessages((previousMessages) => syncMessagesWithCurrentQuestion(previousMessages, data));
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
      return;
    }

    if (!isSkipped) {
      const trimmed = answerText.trim();
      if (trimmed.length < MIN_ANSWER_LENGTH) {
        setSubmitError(`답변은 ${MIN_ANSWER_LENGTH}자 이상 입력해주세요.`);
        return;
      }

      if (trimmed.length > MAX_ANSWER_LENGTH) {
        setSubmitError(`답변은 ${MAX_ANSWER_LENGTH}자 이하로 입력해주세요.`);
        return;
      }
    }

    const currentQuestion = session.currentQuestion;
    const nextAnswerOrder = session.answeredQuestionCount + 1;
    const pendingMessageId = `answer-${Date.now()}`;
    const pendingMessage = createAnswerMessage({
      id: pendingMessageId,
      text: isSkipped ? '이 질문은 건너뛰었습니다.' : answerText.trim(),
      answerOrder: nextAnswerOrder,
      questionId: currentQuestion.id,
      isSkipped,
      pending: true,
    });

    setSubmittingMode(isSkipped ? 'skip' : 'answer');
    setSubmitError(null);
    setTransitionError(null);
    setCompleteError(null);
    setMessages((previousMessages) => [...previousMessages, pendingMessage]);

    try {
      const result = await submitSessionAnswer(session.id, {
        questionId: currentQuestion.id,
        answerOrder: nextAnswerOrder,
        isSkipped,
        ...(isSkipped ? {} : { answerText: answerText.trim() }),
      });

      setMessages((previousMessages) => previousMessages.map((message) => {
        if (message.id !== pendingMessageId) {
          return message;
        }

        return createAnswerMessage({
          id: pendingMessageId,
          text: message.text,
          answerOrder: result.answerOrder,
          questionId: result.questionId,
          isSkipped: result.isSkipped,
          pending: false,
          createdAt: result.submittedAt,
        });
      }));

      await loadSession({ resetAnswerText: true, showPageLoading: false });
    } catch (err) {
      setMessages((previousMessages) =>
        previousMessages.filter((message) => message.id !== pendingMessageId),
      );

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
    setSubmitError(null);
    setCompleteError(null);

    try {
      const result = action === 'pause'
        ? await pauseSession(session.id)
        : await resumeSession(session.id);

      setMessages((previousMessages) => [
        ...previousMessages,
        createSystemMessage(
          result.status === 'paused'
            ? '세션을 일시정지했습니다. 같은 세션으로 다시 돌아와 재개할 수 있습니다.'
            : '세션을 재개했습니다. 이어서 답변을 제출할 수 있습니다.',
          'success',
        ),
      ]);
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
    setTransitionError(null);

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
  const draftStatusText =
    !session?.currentQuestion
      ? '현재 질문 없음'
      : answerText.length === 0
        ? '입력 대기'
        : draftSavedAt
          ? `브라우저 임시 저장됨 · ${formatTime(draftSavedAt)}`
          : '브라우저 임시 저장 중';
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
      <main className="mx-auto max-w-4xl px-4 py-12">
        <p className="text-sm text-zinc-400">세션 정보를 불러오는 중...</p>
      </main>
    );
  }

  if (error || !session) {
    return (
      <main className="mx-auto max-w-4xl px-4 py-12">
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
    <main className="mx-auto max-w-5xl px-4 py-10">
      <div className="mb-8">
        <Link
          href={`/interview/question-sets/${session.questionSetId}`}
          className="text-xs text-zinc-400 hover:text-zinc-600"
        >
          ← 질문 세트 상세로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold text-zinc-900">텍스트 모의 면접</h1>
        <p className="mt-2 text-sm text-zinc-500">
          다음 질문은 항상 서버가 내려주는 currentQuestion 기준으로 이어집니다. 답변 저장 후 세션 상세를 다시 조회해 꼬리질문 또는 다음 기본 질문을 그대로 붙입니다.
        </p>
      </div>

      <section className="rounded-3xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_TONE[session.status]}`}
          >
            상태 {STATUS_LABEL[session.status]}
          </span>
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
            진행 {session.answeredQuestionCount}/{session.totalQuestionCount}
          </span>
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
            남은 질문 {session.remainingQuestionCount}개
          </span>
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-3">
          <div className="rounded-2xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">세션 복원 가능</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">
              {session.resumeAvailable ? '가능' : '불가'}
            </p>
          </div>
          <div className="rounded-2xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">마지막 활동 시각</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">
              {formatDateTime(session.lastActivityAt)}
            </p>
          </div>
          <div className="rounded-2xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">시작 시각</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">
              {formatDateTime(session.startedAt)}
            </p>
          </div>
        </div>

        {session.status === 'paused' && (
          <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-4">
            <p className="text-sm font-semibold text-amber-900">
              {autoPauseLikely ? '자동 일시정지된 것으로 보입니다.' : '현재 일시정지된 세션입니다.'}
            </p>
            <p className="mt-2 text-sm leading-6 text-amber-800">
              {autoPauseLikely
                ? `마지막 활동 후 ${AUTO_PAUSE_THRESHOLD_MINUTES}분 이상 지나 현재 세션이 일시정지 상태로 보입니다. 재개 후 같은 질문부터 이어서 답변을 제출하세요.`
                : '같은 세션에서 그대로 재개할 수 있습니다. 아래 재개 버튼으로 바로 이어서 진행하세요.'}
            </p>
          </div>
        )}

        <div className="mt-6 rounded-3xl border border-zinc-200 bg-zinc-50/60 px-4 py-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-zinc-900">면접 채팅</p>
              <p className="mt-1 text-xs text-zinc-500">
                질문은 left bubble, 답변은 right bubble, 상태 안내는 system bubble로 표시합니다.
              </p>
            </div>
            {session.currentQuestion && (
              <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-zinc-600 shadow-sm">
                현재 Q{session.currentQuestion.questionOrder}
              </span>
            )}
          </div>

          <div className="mt-4 max-h-[34rem] space-y-3 overflow-y-auto rounded-3xl bg-white px-3 py-4">
            {messages.length === 0 && (
              <div className="rounded-2xl border border-dashed border-zinc-200 px-4 py-6 text-center text-sm text-zinc-500">
                현재 질문을 불러오면 여기서 채팅처럼 이어집니다.
              </div>
            )}

            {messages.map((message) => {
              if (message.role === 'system') {
                const toneClass = message.tone === 'success'
                  ? 'border-green-200 bg-green-50 text-green-700'
                  : message.tone === 'warning'
                    ? 'border-amber-200 bg-amber-50 text-amber-800'
                    : 'border-zinc-200 bg-zinc-50 text-zinc-600';

                return (
                  <div key={message.id} className="flex justify-center">
                    <div className={`max-w-xl rounded-full border px-4 py-2 text-xs ${toneClass}`}>
                      {message.text}
                    </div>
                  </div>
                );
              }

              if (message.role === 'answer') {
                return (
                  <div key={message.id} className="flex justify-end">
                    <div className="max-w-2xl rounded-3xl bg-zinc-900 px-4 py-3 text-sm leading-6 text-white shadow-sm">
                      <div className="flex items-center justify-between gap-3">
                        <span className="text-[11px] font-medium text-zinc-300">
                          {message.isSkipped ? '건너뛴 답변' : `답변 ${message.answerOrder ?? ''}`.trim()}
                        </span>
                        {message.pending && (
                          <span className="text-[11px] text-zinc-400">저장 중...</span>
                        )}
                      </div>
                      <p className="mt-2 whitespace-pre-wrap">{message.text}</p>
                    </div>
                  </div>
                );
              }

              return (
                <div key={message.id} className="flex justify-start">
                  <div className="max-w-2xl rounded-3xl border border-zinc-200 bg-white px-4 py-3 text-sm leading-6 text-zinc-900 shadow-sm">
                    <div className="flex flex-wrap items-center gap-2 text-[11px] text-zinc-500">
                      {typeof message.questionOrder === 'number' && (
                        <span className="rounded-full bg-zinc-100 px-2 py-0.5 font-medium text-zinc-700">
                          Q{message.questionOrder}
                        </span>
                      )}
                      {message.questionType && (
                        <span className="rounded-full bg-zinc-100 px-2 py-0.5">
                          {QUESTION_TYPE_LABEL[message.questionType]}
                        </span>
                      )}
                      {message.difficultyLevel && (
                        <span className="rounded-full bg-zinc-100 px-2 py-0.5">
                          {DIFFICULTY_LABEL[message.difficultyLevel] ?? message.difficultyLevel}
                        </span>
                      )}
                    </div>
                    <p className="mt-2 whitespace-pre-wrap">{message.text}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1fr),19rem]">
          <div className="rounded-3xl border border-zinc-200 px-4 py-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-zinc-900">답변 입력</p>
                <p className="mt-1 text-xs text-zinc-500">
                  일반 답변은 {MIN_ANSWER_LENGTH}자 이상 {MAX_ANSWER_LENGTH}자 이하로 입력합니다.
                </p>
                <p className="mt-1 text-xs text-zinc-500">
                  자동 저장 상태: {draftStatusText}
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
              rows={9}
              value={answerText}
              onChange={(event) => setAnswerText(event.target.value)}
              disabled={session.status !== 'in_progress' || !session.currentQuestion || actionBusy}
              placeholder={
                session.status === 'paused'
                  ? '일시정지된 세션은 재개 후 답변을 제출할 수 있습니다.'
                  : session.status !== 'in_progress'
                    ? '현재 상태에서는 답변을 제출할 수 없습니다.'
                    : session.currentQuestion?.questionType === 'follow_up'
                      ? '방금 답변을 더 구체화해서 이어서 적어보세요.'
                      : '면접 답변을 입력하세요.'
              }
              className="mt-4 w-full rounded-3xl border border-zinc-300 px-4 py-3 text-sm leading-6 text-zinc-900 focus:border-zinc-500 focus:outline-none disabled:cursor-not-allowed disabled:bg-zinc-50 disabled:text-zinc-400"
            />

            {submitError && (
              <div className="mt-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {submitError}
              </div>
            )}

            {transitionError && (
              <div className="mt-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {transitionError}
              </div>
            )}

            {completeError && (
              <div className="mt-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {completeError}
              </div>
            )}

            {(session.status === 'completed' || session.status === 'feedback_completed') && (
              <div className="mt-4 rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-600">
                이 세션은 종료되었습니다. 결과 화면에서 리포트를 다시 확인할 수 있습니다.
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

          <div className="space-y-4">
            <div className="rounded-3xl border border-zinc-200 px-4 py-4">
              <p className="text-sm font-semibold text-zinc-900">세션 상태</p>
              <p className="mt-1 text-xs text-zinc-500">
                진행 중에는 일시정지, 일시정지 상태에서는 재개만 허용합니다.
              </p>

              <div className="mt-4 flex flex-wrap gap-3">
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

            <div className="rounded-3xl border border-zinc-200 px-4 py-4">
              <p className="text-sm font-semibold text-zinc-900">세션 종료</p>
              <p className="mt-1 text-xs text-zinc-500">
                남은 질문이 0개가 되면 결과 리포트로 이동할 수 있습니다.
              </p>

              <div className="mt-4 flex flex-wrap gap-3">
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
                  결과 리포트가 준비되었습니다. 결과 확인 버튼으로 이동하세요.
                </p>
              )}
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
