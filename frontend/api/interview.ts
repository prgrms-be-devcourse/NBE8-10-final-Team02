import type {
  ApiFieldError,
  InterviewAnswerSubmitRequest,
  InterviewManualQuestionCreateRequest,
  InterviewQuestion,
  InterviewQuestionSetDetail,
  InterviewQuestionSetCreateRequest,
  InterviewQuestionSetSummary,
  InterviewSession,
  InterviewSessionDetail,
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
