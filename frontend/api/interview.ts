import type {
  ApiFieldError,
  InterviewAnswerSubmitRequest,
  InterviewResult,
  InterviewManualQuestionCreateRequest,
  InterviewQuestion,
  InterviewQuestionSetDetail,
  InterviewQuestionSetCreateRequest,
  InterviewQuestionSetSummary,
  InterviewSession,
  SessionCompletionData,
  InterviewSessionDetail,
  InterviewSessionTransitionData,
  SessionAnswerSubmitData,
} from '@/types/interview';

const base = () =>
  (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000') + '/api/v1/interview';

export class InterviewApiError extends Error {
  code: string | null;
  retryable: boolean | null;
  fieldErrors: ApiFieldError[];

  constructor(message: string, options?: {
    code?: string | null;
    retryable?: boolean | null;
    fieldErrors?: ApiFieldError[];
  }) {
    super(message);
    this.name = 'InterviewApiError';
    this.code = options?.code ?? null;
    this.retryable = options?.retryable ?? null;
    this.fieldErrors = options?.fieldErrors ?? [];
  }
}

async function parseError(res: Response): Promise<InterviewApiError> {
  try {
    const body = await res.json();
    const error = body?.error;

    return new InterviewApiError(
      error?.message ?? `오류가 발생했습니다. (${res.status})`,
      {
        code: error?.code ?? null,
        retryable: error?.retryable ?? null,
        fieldErrors: Array.isArray(error?.fieldErrors) ? (error.fieldErrors as ApiFieldError[]) : [],
      },
    );
  } catch {
    return new InterviewApiError(`오류가 발생했습니다. (${res.status})`);
  }
}

export async function createQuestionSet(
  request: InterviewQuestionSetCreateRequest,
): Promise<InterviewQuestionSetSummary> {
  const res = await fetch(`${base()}/question-sets`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewQuestionSetSummary;
}

/**
 * GET /interview/question-sets
 * 내 질문 세트 목록 조회.
 */
export async function getQuestionSets(): Promise<InterviewQuestionSetSummary[]> {
  const res = await fetch(`${base()}/question-sets`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewQuestionSetSummary[];
}

export async function getQuestionSetDetail(questionSetId: number): Promise<InterviewQuestionSetDetail> {
  const res = await fetch(`${base()}/question-sets/${questionSetId}`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewQuestionSetDetail;
}

export async function addManualQuestion(
  questionSetId: number,
  request: InterviewManualQuestionCreateRequest,
): Promise<InterviewQuestion> {
  const res = await fetch(`${base()}/question-sets/${questionSetId}/questions`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewQuestion;
}

export async function deleteQuestion(questionSetId: number, questionId: number): Promise<void> {
  const res = await fetch(`${base()}/question-sets/${questionSetId}/questions/${questionId}`, {
    method: 'DELETE',
    credentials: 'include',
  });

  if (!res.ok) {
    throw await parseError(res);
  }
}

export async function startSession(questionSetId: number): Promise<InterviewSession> {
  const res = await fetch(`${base()}/sessions`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ questionSetId }),
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewSession;
}

export async function getSessions(): Promise<InterviewSession[]> {
  const res = await fetch(`${base()}/sessions`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewSession[];
}

export async function getSessionDetail(sessionId: number): Promise<InterviewSessionDetail> {
  const res = await fetch(`${base()}/sessions/${sessionId}`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewSessionDetail;
}

export async function submitSessionAnswer(
  sessionId: number,
  request: InterviewAnswerSubmitRequest,
): Promise<SessionAnswerSubmitData> {
  const res = await fetch(`${base()}/sessions/${sessionId}/answers`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as SessionAnswerSubmitData;
}

async function transitionSession(
  sessionId: number,
  action: 'pause' | 'resume',
): Promise<InterviewSessionTransitionData> {
  const res = await fetch(`${base()}/sessions/${sessionId}/${action}`, {
    method: 'POST',
    credentials: 'include',
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewSessionTransitionData;
}

export function pauseSession(sessionId: number): Promise<InterviewSessionTransitionData> {
  return transitionSession(sessionId, 'pause');
}

export function resumeSession(sessionId: number): Promise<InterviewSessionTransitionData> {
  return transitionSession(sessionId, 'resume');
}

export async function completeSession(sessionId: number): Promise<SessionCompletionData> {
  const res = await fetch(`${base()}/sessions/${sessionId}/complete`, {
    method: 'POST',
    credentials: 'include',
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as SessionCompletionData;
}

export async function getSessionResult(sessionId: number): Promise<InterviewResult> {
  const res = await fetch(`${base()}/sessions/${sessionId}/result`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw await parseError(res);
  }

  const body = await res.json();
  return body.data as InterviewResult;
}
