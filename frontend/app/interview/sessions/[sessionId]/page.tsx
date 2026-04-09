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
import {
  shouldAutoCompleteAfterAnswer,
  shouldRequireManualCompleteAfterAnswer,
  shouldShowCompletionFollowupMode,
} from '@/lib/interview-session-flow';
import {
  getSessionActionLabel,
  getSessionStatusSummary,
  SESSION_STATUS_BADGE_META,
} from '@/lib/interview-status-ui';
import { useBrowserSpeechRecognition } from '@/hooks/useBrowserSpeechRecognition';
import type {
  CompletionFollowupAnswerSummary,
  InterviewQuestionType,
  InterviewSessionCurrentQuestion,
  InterviewSessionDetail,
  InterviewSessionTranscriptEntry,
} from '@/types/interview';

const QUESTION_TYPE_LABEL: Record<InterviewQuestionType, string> = {
  experience: '경험',
  project: '프로젝트',
  technical_cs: 'CS',
  technical_stack: '기술 스택',
  behavioral: '행동',
  follow_up: '꼬리 질문',
};

const TRANSCRIPT_ROLE_LABEL: Record<ChatMessageRole, string> = {
  question: '면접관',
  answer: '내 답변',
  system: '상태 안내',
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

interface PendingQuestionFocusRequest {
  previousQuestionId: number | null;
  previousCompletionMode: boolean;
}

type MicrophonePermissionState = 'idle' | 'requesting' | 'granted' | 'denied' | 'unsupported';
type AnswerInputSource = 'typed' | 'voice' | 'mixed';

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

function isTerminalSessionStatus(status: InterviewSessionDetail['status']) {
  return status === 'completed' || status === 'feedback_completed';
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

function formatSummaryAnswerText(answer: CompletionFollowupAnswerSummary) {
  if (answer.isSkipped) {
    return '이 질문은 건너뛰었습니다.';
  }

  return answer.answerText?.trim() || '답변 내용이 없습니다.';
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

function buildTranscriptMessages(entries: InterviewSessionTranscriptEntry[]): ChatMessage[] {
  return entries.flatMap((entry) => [
    createQuestionMessage(entry.question),
    createAnswerMessage({
      id: `answer-${entry.question.id}-${entry.answer.answerOrder}`,
      text: formatSummaryAnswerText(entry.answer),
      answerOrder: entry.answer.answerOrder,
      questionId: entry.question.id,
      isSkipped: entry.answer.isSkipped,
      pending: false,
    }),
  ]);
}

function getLocalSystemMessages(messages: ChatMessage[]): ChatMessage[] {
  return messages.filter((message) => message.role === 'system');
}

function getLocalPendingAnswerMessages(
  messages: ChatMessage[],
  answeredQuestionIds: Set<number>,
): ChatMessage[] {
  return messages.filter((message) =>
    message.role === 'answer'
    && message.pending
    && typeof message.questionId === 'number'
    && !answeredQuestionIds.has(message.questionId),
  );
}

function mergeMessagesWithSessionDetail(
  previousMessages: ChatMessage[],
  session: InterviewSessionDetail,
): ChatMessage[] {
  const transcriptEntries = session.transcriptEntries ?? [];
  const answeredQuestionIds = new Set(
    transcriptEntries.map((entry) => entry.question.id),
  );
  const mergedMessages = [
    ...buildTranscriptMessages(transcriptEntries),
    ...getLocalSystemMessages(previousMessages),
    ...getLocalPendingAnswerMessages(previousMessages, answeredQuestionIds),
  ];

  if (!session.currentQuestion) {
    return mergedMessages;
  }

  const alreadyExists = mergedMessages.some(
    (message) => message.role === 'question' && message.questionId === session.currentQuestion?.id,
  );

  if (alreadyExists) {
    return mergedMessages;
  }

  return [...mergedMessages, createQuestionMessage(session.currentQuestion)];
}

function getTranscriptMessages(messages: ChatMessage[], currentQuestionId: number | null): ChatMessage[] {
  if (currentQuestionId === null) {
    return messages;
  }

  const hasCurrentQuestionAnswer = messages.some(
    (message) => message.role === 'answer' && message.questionId === currentQuestionId,
  );

  return messages.filter(
    (message) => message.role !== 'question' || message.questionId !== currentQuestionId || hasCurrentQuestionAnswer,
  );
}

function mergeVoiceTranscript(currentText: string, nextTranscript: string) {
  const normalizedTranscript = nextTranscript.trim();
  if (!normalizedTranscript) {
    return currentText;
  }

  const normalizedCurrent = currentText.trim();
  if (!normalizedCurrent) {
    return normalizedTranscript;
  }

  if (normalizedCurrent.endsWith(normalizedTranscript)) {
    return currentText;
  }

  return `${normalizedCurrent} ${normalizedTranscript}`.trim();
}

export default function InterviewSessionPage() {
  const params = useParams();
  const router = useRouter();
  const sessionId = Number(params.sessionId);
  const {
    browserSupport,
    supportMessage,
    isListening,
    finalTranscript,
    interimTranscript,
    errorMessage: speechRecognitionErrorMessage,
    startListening,
    stopListening,
    cancelListening,
    resetTranscript,
  } = useBrowserSpeechRecognition();
  const skipNextDraftPersistRef = useRef(false);
  const transcriptInitializedRef = useRef(false);
  const answerSectionRef = useRef<HTMLDivElement | null>(null);
  const answerTextareaRef = useRef<HTMLTextAreaElement | null>(null);
  const pendingQuestionFocusRef = useRef<PendingQuestionFocusRequest | null>(null);
  const committedVoiceTranscriptRef = useRef<string | null>(null);
  const sessionStatusRef = useRef<string | null>(null);
  const autoResumedRef = useRef(false);

  const [session, setSession] = useState<InterviewSessionDetail | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>(() => readStoredMessages(sessionId));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [answerText, setAnswerText] = useState('');
  const [answerInputSource, setAnswerInputSource] = useState<AnswerInputSource | null>(null);
  const [draftSavedAt, setDraftSavedAt] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submittingMode, setSubmittingMode] = useState<'answer' | 'skip' | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);
  const [transitionMode, setTransitionMode] = useState<'pause' | 'resume' | null>(null);
  const [completeError, setCompleteError] = useState<string | null>(null);
  const [completingSession, setCompletingSession] = useState(false);
  const [transcriptCollapsed, setTranscriptCollapsed] = useState(true);
  const [needsManualCompleteAfterCompletionAnswer, setNeedsManualCompleteAfterCompletionAnswer] =
    useState(false);
  const [justResumed, setJustResumed] = useState(false);
  const [microphonePermission, setMicrophonePermission] = useState<MicrophonePermissionState>('idle');
  const [voiceRuntimeError, setVoiceRuntimeError] = useState<string | null>(null);
  const [voiceCaptureStatusMessage, setVoiceCaptureStatusMessage] = useState<string | null>(null);
  const currentQuestionId = session?.currentQuestion?.id ?? null;
  const completionFollowupContext = session?.completionFollowupContext ?? null;
  const isCompletionFollowupMode = !!completionFollowupContext;
  const transcriptMessages = getTranscriptMessages(messages, currentQuestionId);
  const latestTranscriptSystemMessage =
    [...transcriptMessages].reverse().find((message) => message.role === 'system') ?? null;
  const hasTranscriptHistory = transcriptMessages.length > 0;
  const transcriptQuestionCount = transcriptMessages.filter((message) => message.role === 'question').length;
  const transcriptAnswerCount = transcriptMessages.filter((message) => message.role === 'answer').length;

  useEffect(() => {
    setMessages(readStoredMessages(sessionId));
  }, [sessionId]);

  useEffect(() => {
    transcriptInitializedRef.current = false;
    setTranscriptCollapsed(false);
    committedVoiceTranscriptRef.current = null;
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
      setAnswerInputSource(null);
      setDraftSavedAt(null);
      return;
    }

    const storedDraft = readStoredDraft(sessionId);
    if (!storedDraft || storedDraft.questionId !== currentQuestionId) {
      if (storedDraft) {
        writeStoredDraft(sessionId, null);
      }
      setAnswerText('');
      setAnswerInputSource(null);
      setDraftSavedAt(null);
      return;
    }

    setAnswerText(storedDraft.text);
    setAnswerInputSource(storedDraft.text.trim().length > 0 ? 'typed' : null);
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
      if (data.status !== 'in_progress') {
        setJustResumed(false);
      }
      setMessages((previousMessages) => mergeMessagesWithSessionDetail(previousMessages, data));
      if (options?.resetAnswerText) {
        setAnswerText('');
        setAnswerInputSource(null);
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

  // sessionStatusRef를 최신 session.status와 동기화 (cleanup/beforeunload에서 stale 방지)
  useEffect(() => {
    sessionStatusRef.current = session?.status ?? null;
  }, [session]);

  // paused 상태로 진입 시 자동 재개
  useEffect(() => {
    if (autoResumedRef.current || !session || session.status !== 'paused' || !session.resumeAvailable) return;
    autoResumedRef.current = true;
    void resumeSession(session.id)
      .then(() => loadSession({ showPageLoading: false }))
      .catch(() => { autoResumedRef.current = false; });
  }, [session, loadSession]);

  // in_progress 상태에서 이탈 시 자동 일시정지
  useEffect(() => {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';
    const pauseUrl = `${apiBase}/api/v1/interview/sessions/${sessionId}/pause`;

    function handleBeforeUnload() {
      if (sessionStatusRef.current === 'in_progress') {
        navigator.sendBeacon(pauseUrl);
      }
    }

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      if (sessionStatusRef.current === 'in_progress') {
        void pauseSession(sessionId);
      }
    };
  }, [sessionId]);

  useEffect(() => {
    if (!session || transcriptInitializedRef.current) {
      return;
    }

    const storedMessages = readStoredMessages(sessionId);
    const hasInitialTranscriptHistory =
      session.transcriptEntries.length > 0
      || storedMessages.some((message) => message.role === 'system' || message.pending);
    setTranscriptCollapsed(hasInitialTranscriptHistory);
    transcriptInitializedRef.current = true;
  }, [session, sessionId]);

  const stopVoiceCaptureForBoundary = useCallback((options?: { clearMessages?: boolean }) => {
    cancelListening();
    committedVoiceTranscriptRef.current = null;
    if (options?.clearMessages) {
      setVoiceRuntimeError(null);
      setVoiceCaptureStatusMessage(null);
    }
  }, [cancelListening]);

  useEffect(() => {
    if (browserSupport === 'supported') {
      setMicrophonePermission((current) => (current === 'unsupported' ? 'idle' : current));
      return;
    }

    setMicrophonePermission('unsupported');
  }, [browserSupport]);

  const requestMicrophonePermission = useCallback(async () => {
    if (browserSupport !== 'supported') {
      setMicrophonePermission('unsupported');
      return false;
    }

    if (!navigator.mediaDevices?.getUserMedia) {
      setMicrophonePermission('denied');
      setVoiceRuntimeError('마이크 권한을 확인할 수 없어 텍스트 입력으로 계속 진행합니다.');
      return false;
    }

    setMicrophonePermission('requesting');
    setVoiceRuntimeError(null);

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach((track) => track.stop());
      setMicrophonePermission('granted');
      return true;
    } catch (permissionError) {
      setMicrophonePermission('denied');
      if (permissionError instanceof DOMException && permissionError.name === 'NotFoundError') {
        setVoiceRuntimeError('사용 가능한 마이크를 찾지 못했습니다. 텍스트 입력으로 계속 진행해주세요.');
      } else {
        setVoiceRuntimeError('마이크 권한을 사용할 수 없어 텍스트 입력으로 계속 진행합니다.');
      }
      return false;
    }
  }, [browserSupport]);

  useEffect(() => {
    if (!session || browserSupport !== 'supported' || microphonePermission !== 'idle') {
      return;
    }

    void requestMicrophonePermission();
  }, [browserSupport, microphonePermission, requestMicrophonePermission, session]);

  useEffect(() => {
    if (!speechRecognitionErrorMessage) {
      return;
    }

    setVoiceRuntimeError(speechRecognitionErrorMessage);
    setVoiceCaptureStatusMessage(null);
  }, [speechRecognitionErrorMessage]);

  useEffect(() => {
    if (isListening) {
      return;
    }

    const normalizedTranscript = finalTranscript.trim();
    if (!normalizedTranscript || committedVoiceTranscriptRef.current === normalizedTranscript) {
      return;
    }

    committedVoiceTranscriptRef.current = normalizedTranscript;
    setAnswerText((previousText) => mergeVoiceTranscript(previousText, normalizedTranscript));
    setAnswerInputSource((current) => {
      if (current === 'typed' || current === 'mixed') {
        return 'mixed';
      }

      return 'voice';
    });
    setVoiceRuntimeError(null);
    setVoiceCaptureStatusMessage('음성 transcript가 답변 입력란에 반영되었습니다. 제출 전에 문장을 다듬어주세요.');
    resetTranscript();
  }, [finalTranscript, isListening, resetTranscript]);

  useEffect(() => {
    stopVoiceCaptureForBoundary({ clearMessages: true });
  }, [currentQuestionId, sessionId, stopVoiceCaptureForBoundary]);

  const handleAnswerTextChange = useCallback((nextText: string) => {
    setAnswerText(nextText);
    setAnswerInputSource((current) => {
      if (nextText.trim().length === 0) {
        return null;
      }

      if (current === 'voice' || current === 'mixed') {
        return 'mixed';
      }

      return 'typed';
    });
  }, []);

  const handleStartVoiceCapture = useCallback(async () => {
    if (!session?.currentQuestion || session.status !== 'in_progress') {
      return;
    }

    let permissionGranted = microphonePermission === 'granted';
    if (!permissionGranted) {
      permissionGranted = await requestMicrophonePermission();
    }

    if (!permissionGranted) {
      return;
    }

    committedVoiceTranscriptRef.current = null;
    setVoiceRuntimeError(null);
    setVoiceCaptureStatusMessage(null);
    startListening();
  }, [microphonePermission, requestMicrophonePermission, session, startListening]);

  const handleStopVoiceCapture = useCallback(() => {
    if (!isListening) {
      return;
    }

    setVoiceCaptureStatusMessage('음성 입력을 정리 중입니다. transcript가 입력란에 반영되면 문장을 확인해주세요.');
    stopListening();
  }, [isListening, stopListening]);

  const recoverSessionForCompleteFallback = useCallback(async () => {
    if (!Number.isFinite(sessionId)) {
      return null;
    }

    try {
      const data = await getSessionDetail(sessionId);
      setSession(data);
      if (data.status !== 'in_progress') {
        setJustResumed(false);
      }
      setMessages((previousMessages) => mergeMessagesWithSessionDetail(previousMessages, data));
      return data;
    } catch {
      return null;
    }
  }, [sessionId]);

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

  async function attemptSessionComplete() {
    if (!session) {
      return;
    }

    stopVoiceCaptureForBoundary({ clearMessages: true });

    const moveToResultPage = () => {
      pendingQuestionFocusRef.current = null;
      router.push(`/interview/sessions/${session.id}/result`);
    };

    setCompletingSession(true);
    setCompleteError(null);
    setSubmitError(null);
    setTransitionError(null);
    setNeedsManualCompleteAfterCompletionAnswer(false);

    try {
      await completeSession(session.id);
      moveToResultPage();
    } catch (err) {
      if (
        err instanceof InterviewApiError &&
        (err.retryable || err.code === 'INTERVIEW_SESSION_ALREADY_COMPLETED')
      ) {
        moveToResultPage();
        return;
      }

      if (err instanceof InterviewApiError && err.code === 'INTERVIEW_SESSION_STATUS_CONFLICT') {
        const refreshed = await recoverSessionForCompleteFallback();
        if (refreshed && isTerminalSessionStatus(refreshed.status)) {
          moveToResultPage();
        }
        return;
      }

      const refreshed = await recoverSessionForCompleteFallback();
      if (refreshed && isTerminalSessionStatus(refreshed.status)) {
        moveToResultPage();
        return;
      }

      if (err instanceof InterviewApiError) {
        if (
          shouldShowCompletionFollowupMode({
            errorCode: err.code,
            fieldErrors: err.fieldErrors,
            refreshedSession: refreshed,
          })
        ) {
          setMessages((previousMessages) => [
            ...previousMessages,
            createSystemMessage('보완 질문이 추가되었습니다. 이어서 답변을 제출해주세요.', 'warning'),
          ]);
          return;
        }
      }

      pendingQuestionFocusRef.current = null;
      setCompleteError(formatApiError(err, '세션을 종료하지 못했습니다.'));
    } finally {
      setCompletingSession(false);
    }
  }

  async function handleSubmitAnswer(isSkipped: boolean) {
    if (!session?.currentQuestion) {
      setSubmitError('현재 제출 가능한 질문이 없습니다.');
      return;
    }

    if (isListening) {
      setSubmitError('음성 입력을 먼저 중지하고 transcript를 확인해주세요.');
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
    const wasCompletionFollowup =
      !!completionFollowupContext
      && completionFollowupContext.completionFollowupQuestion.id === currentQuestion.id;
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
    setNeedsManualCompleteAfterCompletionAnswer(false);
    setVoiceRuntimeError(null);
    setVoiceCaptureStatusMessage(null);
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

      pendingQuestionFocusRef.current = {
        previousQuestionId: currentQuestion.id,
        previousCompletionMode: wasCompletionFollowup,
      };

      const refreshed = await loadSession({ resetAnswerText: true, showPageLoading: false });
      if (
        shouldAutoCompleteAfterAnswer({
          wasCompletionFollowup,
          refreshedSession: refreshed,
        })
      ) {
        await attemptSessionComplete();
        return;
      }

      if (
        shouldRequireManualCompleteAfterAnswer({
          wasCompletionFollowup,
          refreshedSession: refreshed,
        })
      ) {
        pendingQuestionFocusRef.current = null;
        setNeedsManualCompleteAfterCompletionAnswer(true);
        setMessages((previousMessages) => [
          ...previousMessages,
          createSystemMessage('보완 질문 답변이 저장되었습니다. 세션 종료를 다시 눌러 결과를 생성하세요.', 'warning'),
        ]);
        return;
      }

      if (!refreshed || refreshed.status !== 'in_progress' || !refreshed.currentQuestion) {
        pendingQuestionFocusRef.current = null;
      }
    } catch (err) {
      pendingQuestionFocusRef.current = null;
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

    stopVoiceCaptureForBoundary({ clearMessages: true });

    setTransitionMode(action);
    setTransitionError(null);
    setSubmitError(null);
    setCompleteError(null);

    try {
      const result = action === 'pause'
        ? await pauseSession(session.id)
        : await resumeSession(session.id);

      setJustResumed(result.status === 'in_progress');

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
    stopVoiceCaptureForBoundary({ clearMessages: true });
    await attemptSessionComplete();
  }

  const answerLength = answerText.trim().length;
  const actionBusy = submittingMode !== null || transitionMode !== null || completingSession;
  const autoPauseLikely = session ? isAutoPauseLikely(session) : false;
  const sessionStatusSummary = session
    ? getSessionStatusSummary({
      status: session.status,
      autoPauseLikely,
      justResumed: justResumed && session.status === 'in_progress',
    })
    : null;
  const draftStatusText =
    !session?.currentQuestion
      ? '현재 질문 없음'
      : answerText.length === 0
        ? '입력 대기'
        : draftSavedAt
          ? `브라우저 임시 저장됨 · ${formatTime(draftSavedAt)}`
          : '브라우저 임시 저장 중';
  const answerInputSourceLabel =
    answerInputSource === 'voice'
      ? '음성 transcript 반영'
      : answerInputSource === 'mixed'
        ? '음성 transcript + 수동 수정'
        : answerInputSource === 'typed'
          ? '직접 입력'
          : '입력 대기';
  const canSubmitAnswer =
    session?.status === 'in_progress' &&
    !!session.currentQuestion &&
    answerLength >= MIN_ANSWER_LENGTH &&
    answerLength <= MAX_ANSWER_LENGTH &&
    !actionBusy &&
    !isListening;
  const canSkip =
    session?.status === 'in_progress'
    && !!session.currentQuestion
    && !actionBusy
    && !isListening;
  const canPause = session?.status === 'in_progress' && !actionBusy;
  const canResume = session?.status === 'paused' && !actionBusy;
  const canComplete =
    !!session &&
    session.remainingQuestionCount === 0 &&
    (session.status === 'in_progress' || session.status === 'paused') &&
    !actionBusy;
  const showManagementCard =
    session?.status === 'in_progress' || session?.status === 'paused';
  const pausedSessionSummary = session?.status === 'paused'
    ? sessionStatusSummary
    : null;
  const isCompletionCurrentQuestion =
    !!session?.currentQuestion
    && !!completionFollowupContext
    && completionFollowupContext.completionFollowupQuestion.id === session.currentQuestion.id;
  const currentQuestionTypeLabel = session?.currentQuestion
    ? isCompletionCurrentQuestion
      ? '보완 질문'
      : QUESTION_TYPE_LABEL[session.currentQuestion.questionType]
    : null;
  const currentQuestionHint = isCompletionCurrentQuestion
    ? '이 질문은 이전 답변 전체를 바탕으로 AI가 추가 확인이 필요하다고 판단한 보완 질문입니다.'
    : session?.currentQuestion?.questionType === 'follow_up'
      ? '직전 답변을 더 구체화해 설명하는 꼬리 질문입니다.'
      : '지금 이 질문에 대한 답변을 작성하면 다음 질문은 서버의 currentQuestion 기준으로 이어집니다. 음성 transcript를 사용하더라도 제출 전 textarea를 최종 기준으로 확인합니다.';
  const completeButtonDescription = needsManualCompleteAfterCompletionAnswer
    ? '보완 질문 답변이 끝났습니다. 종료 버튼을 다시 눌러 결과를 생성합니다.'
    : isCompletionFollowupMode
    ? '보완 질문 답변 후 다시 종료를 눌러 결과를 생성합니다.'
    : session?.status === 'paused'
    ? '같은 세션을 재개하거나, 답변이 모두 끝났다면 종료를 진행할 수 있습니다.'
    : '필요하면 잠시 일시정지한 뒤 다시 이어가거나, 답변이 모두 끝났다면 종료를 진행할 수 있습니다. 그냥 나가더라도 상태는 저장됩니다.';
  const voiceSectionBadge = isListening
    ? { label: '음성 입력 중', tone: 'bg-emerald-50 text-emerald-700' }
    : microphonePermission === 'granted'
      ? { label: '음성 입력 가능', tone: 'bg-blue-50 text-blue-700' }
      : microphonePermission === 'requesting'
        ? { label: '권한 확인 중', tone: 'bg-amber-50 text-amber-700' }
        : microphonePermission === 'denied'
          ? { label: '권한 필요', tone: 'bg-amber-50 text-amber-700' }
          : { label: '텍스트 입력', tone: 'bg-zinc-100 text-zinc-600' };
  const voiceSupportDescription =
    browserSupport === 'unsupported'
      ? supportMessage
      : microphonePermission === 'denied'
        ? '마이크 권한을 켜주세요. 권한을 허용하면 음성 입력을 사용할 수 있습니다.'
        : microphonePermission === 'requesting'
          ? '마이크 권한을 확인하고 있습니다.'
          : microphonePermission === 'granted'
            ? '시작 후 답변하고, 중지한 뒤 내용을 확인하세요.'
            : '음성 입력 준비 상태를 확인 중입니다.';
  const canStartVoiceCapture =
    browserSupport === 'supported'
    && microphonePermission === 'granted'
    && session?.status === 'in_progress'
    && !!session.currentQuestion
    && !actionBusy
    && !isListening;
  const canRetryMicrophonePermission =
    browserSupport === 'supported'
    && microphonePermission === 'denied';

  useEffect(() => {
    const pendingFocus = pendingQuestionFocusRef.current;

    if (
      !pendingFocus ||
      !session ||
      actionBusy ||
      session.status !== 'in_progress' ||
      !session.currentQuestion
    ) {
      return;
    }

    const enteredCompletionFollowup = isCompletionFollowupMode && !pendingFocus.previousCompletionMode;
    const questionChanged = pendingFocus.previousQuestionId !== session.currentQuestion.id;

    pendingQuestionFocusRef.current = null;

    if (!questionChanged && !enteredCompletionFollowup) {
      return;
    }

    const anchor = answerSectionRef.current ?? answerTextareaRef.current;
    anchor?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    answerTextareaRef.current?.focus();
  }, [actionBusy, isCompletionFollowupMode, session]);

  // 질문이 처음 로드되거나 바뀔 때 textarea 자동 포커스
  useEffect(() => {
    if (!session || session.status !== 'in_progress' || !session.currentQuestion) return;
    answerTextareaRef.current?.focus();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.currentQuestion?.id]);

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
        <h1 className="mt-2 text-2xl font-semibold text-zinc-900">실시간 모의 면접</h1>
        {/*<p className="mt-2 text-sm text-zinc-500">*/}
        {/*  다음 질문은 항상 서버가 내려주는 currentQuestion 기준으로 이어집니다. 음성 transcript는 보조 입력 계층으로만 쓰고, 제출은 최종 textarea 기준으로 처리합니다.*/}
        {/*</p>*/}
      </div>

      <section className="rounded-3xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        {/*<div className="flex flex-wrap items-center gap-2">*/}
        {/*  <span*/}
        {/*    className={`rounded-full px-2 py-0.5 text-xs font-medium ${SESSION_STATUS_BADGE_META[session.status].tone}`}*/}
        {/*  >*/}
        {/*    상태 {SESSION_STATUS_BADGE_META[session.status].label}*/}
        {/*  </span>*/}
        {/*  <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">*/}
        {/*    진행 {session.answeredQuestionCount}/{session.totalQuestionCount}*/}
        {/*  </span>*/}
        {/*  <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">*/}
        {/*    남은 질문 {session.remainingQuestionCount}개*/}
        {/*  </span>*/}
        {/*</div>*/}

        <div className="grid gap-3 md:grid-cols-3">
          <div className="rounded-2xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">상태
            <span className={`ml-3 mt-1.5 inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${SESSION_STATUS_BADGE_META[session.status].tone}`}>
              {SESSION_STATUS_BADGE_META[session.status].label}
            </span></p>
            <div className="mt-3 flex gap-4 border-t border-zinc-200 pt-3 text-xs text-zinc-500">
              <span>진행 <span className="font-semibold text-zinc-800">{session.answeredQuestionCount}/{session.totalQuestionCount}</span></span>
              <span>남은 질문 <span className="font-semibold text-zinc-800">{session.remainingQuestionCount}개</span></span>
            </div>
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

        {pausedSessionSummary && (
          <div className={`mt-5 rounded-2xl border px-4 py-4 ${pausedSessionSummary.tone}`}>
            <p className={`text-xs font-semibold uppercase tracking-[0.12em] ${pausedSessionSummary.eyebrowTone}`}>
              {pausedSessionSummary.eyebrow}
            </p>
            <p className={`mt-2 text-base font-semibold ${pausedSessionSummary.titleTone}`}>
              {pausedSessionSummary.title}
            </p>
            <p className={`mt-2 text-sm leading-6 ${pausedSessionSummary.descriptionTone}`}>
              {pausedSessionSummary.description}
            </p>
          </div>
        )}

        {session && isTerminalSessionStatus(session.status) && (
          <div className="mt-5 flex justify-end">
            <Link
              href={`/interview/sessions/${session.id}/result`}
              className={`rounded-full border bg-white px-4 py-2.5 text-sm font-medium ${
                session.status === 'completed'
                  ? 'border-cyan-300 text-cyan-900'
                  : 'border-blue-300 text-blue-900'
              }`}
            >
              {getSessionActionLabel(session.status)}
            </Link>
          </div>
        )}

        {hasTranscriptHistory && (
          <div className="mt-6 rounded-3xl border border-zinc-200/80 bg-zinc-50/70 px-4 py-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-zinc-900">문맥 및 상태관리</p>
                <p className="mt-1 text-xs text-zinc-500">
                  이전 질문/답변과 상태 안내를 참고용으로만 펼쳐 확인합니다.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setTranscriptCollapsed((current) => !current)}
                className="rounded-full border border-zinc-300 bg-white px-3 py-1 text-xs font-medium text-zinc-600 shadow-sm"
              >
                {transcriptCollapsed ? '이전 문맥 펼치기' : '이전 문맥 접기'}
              </button>
            </div>

            {transcriptCollapsed ? (
              <div className="mt-4 rounded-3xl border border-dashed border-zinc-200 bg-white/90 px-4 py-4 text-sm text-zinc-500">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full bg-zinc-100 px-2.5 py-1 text-[11px] font-medium text-zinc-700">
                    질문 {transcriptQuestionCount}개
                  </span>
                  <span className="rounded-full bg-zinc-100 px-2.5 py-1 text-[11px] font-medium text-zinc-700">
                    답변 {transcriptAnswerCount}개
                  </span>
                  {latestTranscriptSystemMessage && (
                    <span className="rounded-full bg-amber-100 px-2.5 py-1 text-[11px] font-medium text-amber-800">
                      상태 안내 1건
                    </span>
                  )}
                </div>
                <p className="mt-3">
                  이전 질문/답변 기록은 접혀 있습니다. 필요하면 위 버튼으로 다시 펼쳐 확인할 수 있습니다.
                </p>
                {latestTranscriptSystemMessage && (
                  <div className="mt-3 rounded-2xl border border-amber-200 bg-amber-50 px-3 py-3 text-xs text-amber-800">
                    <p className="text-[11px] font-semibold tracking-[0.08em] text-amber-700">
                      {TRANSCRIPT_ROLE_LABEL.system}
                    </p>
                    <p className="mt-1.5 leading-5">{latestTranscriptSystemMessage.text}</p>
                  </div>
                )}
              </div>
            ) : (
              <div className="mt-4 h-[20rem] min-h-[12rem] max-h-[36rem] resize-y space-y-2.5 overflow-auto rounded-3xl border border-zinc-200/80 bg-white/90 px-3 py-3">
                {transcriptMessages.map((message) => {
                  if (message.role === 'system') {
                    const toneClass = message.tone === 'success'
                      ? 'border-green-200 bg-green-50/90 text-green-700'
                      : message.tone === 'warning'
                        ? 'border-amber-200 bg-amber-50/90 text-amber-800'
                        : 'border-zinc-200 bg-zinc-50 text-zinc-600';

                    return (
                      <div key={message.id} className="flex justify-center">
                        <div className={`max-w-xl rounded-2xl border px-4 py-3 text-xs ${toneClass}`}>
                          <p className="text-[11px] font-semibold tracking-[0.08em]">
                            {TRANSCRIPT_ROLE_LABEL.system}
                          </p>
                          <p className="mt-1.5 leading-5">{message.text}</p>
                        </div>
                      </div>
                    );
                  }

                  if (message.role === 'answer') {
                    return (
                      <div key={message.id} className="flex justify-end">
                        <div className="max-w-2xl rounded-3xl bg-zinc-800 px-4 py-3 text-sm leading-6 text-white">
                          <p className="text-[11px] font-semibold tracking-[0.08em] text-zinc-300">
                            {TRANSCRIPT_ROLE_LABEL.answer}
                          </p>
                          <div className="mt-2 flex items-center justify-between gap-3">
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
                      <div className="max-w-2xl rounded-3xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm leading-6 text-zinc-900">
                        <p className="text-[11px] font-semibold tracking-[0.08em] text-zinc-500">
                          {TRANSCRIPT_ROLE_LABEL.question}
                        </p>
                        <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-zinc-500">
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
            )}
          </div>
        )}

        {completionFollowupContext && (
          <div className="mt-4 rounded-3xl border border-blue-200 bg-blue-50 px-4 py-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-blue-900">보완 질문 배경</p>
                <p className="mt-1 text-xs text-blue-700">
                  이 질문이 나온 배경을 먼저 확인한 뒤 아래 현재 질문에 이어서 답변해주세요.
                </p>
              </div>
              <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-blue-700 shadow-sm">
                기준 Q{completionFollowupContext.parentQuestionOrder}
              </span>
            </div>

            <div className="mt-4 space-y-3">
              <div className="rounded-2xl border border-blue-100 bg-white px-4 py-3">
                <p className="text-[11px] font-medium text-blue-700">
                  기준 질문 Q{completionFollowupContext.rootQuestion.questionOrder}
                </p>
                <p className="mt-2 text-sm leading-6 text-zinc-900">
                  {completionFollowupContext.rootQuestion.questionText}
                </p>
                <div className="mt-3 rounded-2xl bg-zinc-50 px-3 py-3 text-sm leading-6 text-zinc-700">
                  {formatSummaryAnswerText(completionFollowupContext.rootAnswer)}
                </div>
              </div>

              {completionFollowupContext.runtimeFollowupQuestion && completionFollowupContext.runtimeFollowupAnswer && (
                <div className="rounded-2xl border border-blue-100 bg-white px-4 py-3">
                  <p className="text-[11px] font-medium text-blue-700">
                    이전 꼬리 질문 Q{completionFollowupContext.runtimeFollowupQuestion.questionOrder}
                  </p>
                  <p className="mt-2 text-sm leading-6 text-zinc-900">
                    {completionFollowupContext.runtimeFollowupQuestion.questionText}
                  </p>
                  <div className="mt-3 rounded-2xl bg-zinc-50 px-3 py-3 text-sm leading-6 text-zinc-700">
                    {formatSummaryAnswerText(completionFollowupContext.runtimeFollowupAnswer)}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        <div className={`mt-4 grid gap-4 ${showManagementCard ? 'lg:grid-cols-[minmax(0,1fr),19rem]' : ''}`}>
          <div className="space-y-4">
            <section
              aria-labelledby="current-answer-work-heading"
              className="overflow-hidden rounded-[2rem] border border-zinc-300 bg-white shadow-md ring-2 ring-zinc-200"
            >
              {session.currentQuestion && (
                <div className="border-b border-zinc-100 bg-zinc-50/40 px-5 py-5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-zinc-700 shadow-sm">
                      현재 Q{session.currentQuestion.questionOrder}
                    </span>
                    <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-zinc-600 shadow-sm">
                      {currentQuestionTypeLabel}
                    </span>
                    <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-zinc-600 shadow-sm">
                      {DIFFICULTY_LABEL[session.currentQuestion.difficultyLevel] ?? session.currentQuestion.difficultyLevel}
                    </span>
                  </div>
                  <p className="mt-4 text-lg font-semibold leading-7 text-zinc-900">
                    {session.currentQuestion.questionText}
                  </p>
                  <p className="mt-3 text-sm leading-5 text-zinc-500">
                    {currentQuestionHint}
                  </p>
                </div>
              )}

              <div ref={answerSectionRef} className="bg-white px-5 py-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold text-zinc-900">답변 입력</p>
                    <p className="mt-1 text-xs text-zinc-500">
                      일반 답변은 {MIN_ANSWER_LENGTH}자 이상 {MAX_ANSWER_LENGTH}자 이하로 입력합니다.
                    </p>
                    <p className="mt-0.5 text-[10px] text-zinc-400">
                      자동 저장: {draftStatusText} · 입력 기준: {answerInputSourceLabel}
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

                <div className="mt-4 rounded-3xl border border-zinc-200 bg-zinc-50 px-4 py-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-semibold text-zinc-900">음성 입력</p>
                      <p className="mt-1 text-xs text-zinc-500">
                        Chrome, Edge 등 일부 브라우저에서 음성 입력을 지원합니다.
                      </p>
                    </div>
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${voiceSectionBadge.tone}`}>
                      {voiceSectionBadge.label}
                    </span>
                  </div>

                  <p className="mt-3 text-xs leading-5 text-zinc-600">
                    {voiceSupportDescription}
                  </p>

                  {voiceRuntimeError && (
                    <div className="mt-3 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                      {voiceRuntimeError}
                    </div>
                  )}

                  {voiceCaptureStatusMessage && (
                    <div className="mt-3 rounded-2xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">
                      {voiceCaptureStatusMessage}
                    </div>
                  )}

                  {(isListening
                    || finalTranscript.length > 0
                    || interimTranscript.length > 0) && (
                    <div className="mt-4 grid gap-3 md:grid-cols-2">
                      <div className="rounded-2xl border border-zinc-200 bg-white px-4 py-3">
                        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-zinc-500">
                          확정 transcript
                        </p>
                        <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-zinc-900">
                          {finalTranscript || '아직 확정된 음성 transcript가 없습니다.'}
                        </p>
                      </div>
                      <div className="rounded-2xl border border-emerald-200 bg-emerald-50/70 px-4 py-3">
                        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-emerald-700">
                          실시간 interim transcript
                        </p>
                        <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-emerald-900">
                          {interimTranscript || '음성을 듣는 동안 여기에 실시간 transcript가 표시됩니다.'}
                        </p>
                      </div>
                    </div>
                  )}

                  <div className="mt-4 flex flex-wrap gap-3">
                    <button
                      type="button"
                      onClick={() => void handleStartVoiceCapture()}
                      disabled={!canStartVoiceCapture}
                      className="rounded-full border border-blue-300 bg-white px-4 py-2.5 text-sm font-medium text-blue-900 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {microphonePermission === 'requesting'
                        ? '권한 확인 중...'
                        : isListening
                          ? '음성 입력 중...'
                          : '음성 입력 시작'}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleStopVoiceCapture()}
                      disabled={!isListening}
                      className="rounded-full border border-emerald-300 bg-white px-4 py-2.5 text-sm font-medium text-emerald-900 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      음성 입력 중지
                    </button>
                    {canRetryMicrophonePermission && (
                      <button
                        type="button"
                        onClick={() => void requestMicrophonePermission()}
                        className="rounded-full border border-zinc-300 bg-white px-4 py-2.5 text-sm font-medium text-zinc-700"
                      >
                        마이크 권한 다시 요청
                      </button>
                    )}
                  </div>
                </div>

                <textarea
                  ref={answerTextareaRef}
                  rows={9}
                  value={answerText}
                  onChange={(event) => handleAnswerTextChange(event.target.value)}
                  disabled={session.status !== 'in_progress' || !session.currentQuestion || actionBusy}
                  placeholder={
                    session.status === 'paused'
                      ? '일시정지된 세션은 재개 후 답변을 제출할 수 있습니다.'
                      : session.status !== 'in_progress'
                        ? '현재 상태에서는 답변을 제출할 수 없습니다.'
                        : microphonePermission === 'granted'
                          ? '음성 transcript가 반영되면 여기서 최종 문장을 다듬거나 직접 입력으로 이어서 작성할 수 있습니다.'
                        : session.currentQuestion?.questionType === 'follow_up'
                          ? '방금 답변을 더 구체화해서 이어서 적어보세요.'
                          : '면접 답변을 입력하세요.'
                  }
                  className="mt-4 w-full resize-none rounded-3xl border border-zinc-400 px-4 py-3 text-sm leading-6 text-zinc-900 focus:border-zinc-600 focus:outline-none focus:ring-2 focus:ring-zinc-300 disabled:cursor-not-allowed disabled:bg-zinc-50 disabled:text-zinc-400"
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

                {needsManualCompleteAfterCompletionAnswer && (
                  <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">
                    세션 종료를 다시 눌러 결과를 생성하세요.
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
            </section>
          </div>

          {showManagementCard && (
            <div className="space-y-4">
              <div className="rounded-3xl border border-zinc-200 px-4 py-4">
                <p className="text-sm font-semibold text-zinc-900">세션 관리</p>
                <p className="mt-1 text-xs text-zinc-500">
                  {completeButtonDescription}
                </p>

                <div className="mt-4 flex flex-wrap gap-3">
                  <button
                    type="button"
                    onClick={() => void handleTransition('pause')}
                    disabled={!canPause}
                    className="rounded-full border border-amber-300 bg-white px-3 py-2 text-xs font-medium text-amber-900 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {transitionMode === 'pause' ? '일시정지 중...' : '일시정지'}
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleTransition('resume')}
                    disabled={!canResume}
                    className="rounded-full border border-green-300 bg-white px-3 py-2 text-xs font-medium text-green-900 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {transitionMode === 'resume' ? '재개 중...' : '재개'}
                  </button>
                </div>

                <div className="mt-4 flex flex-wrap gap-3">
                  <button
                    type="button"
                    onClick={() => void handleCompleteSession()}
                    disabled={!canComplete}
                    className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {completingSession ? '세션 종료 중...' : '세션 종료(면접 결과 보기)'}
                  </button>
                </div>

                {session.remainingQuestionCount > 0 && (
                  <p className="mt-3 text-sm text-amber-700">
                    남은 질문이 {session.remainingQuestionCount}개라서 아직 세션을 종료할 수 없습니다.
                  </p>
                )}

                {needsManualCompleteAfterCompletionAnswer && (
                  <p className="mt-3 text-sm text-blue-700">
                    보완 질문 답변이 저장되었습니다. 종료 버튼으로 결과 생성을 마무리하세요.
                  </p>
                )}
              </div>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
