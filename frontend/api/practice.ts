import type {
  PracticeQuestion,
  PracticeSessionResponse,
  PracticeSessionDetailResponse,
  PracticeTag,
  SubmitPracticeAnswerRequest,
  PracticePagination,
} from '@/types/practice';

const base = () =>
  (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000') + '/api/v1/practice';

export class PracticeApiError extends Error {
  code: string | null;
  retryable: boolean | null;

  constructor(message: string, options?: { code?: string | null; retryable?: boolean | null }) {
    super(message);
    this.name = 'PracticeApiError';
    this.code = options?.code ?? null;
    this.retryable = options?.retryable ?? null;
  }
}

async function parseError(res: Response): Promise<PracticeApiError> {
  try {
    const body = await res.json();
    const error = body?.error;
    return new PracticeApiError(error?.message ?? `오류가 발생했습니다. (${res.status})`, {
      code: error?.code ?? null,
      retryable: error?.retryable ?? null,
    });
  } catch {
    return new PracticeApiError(`오류가 발생했습니다. (${res.status})`);
  }
}

export async function getQuestions(params: {
  tagIds?: number[];
  questionType?: string;
  page?: number;
  size?: number;
}): Promise<{ data: PracticeQuestion[]; pagination: PracticePagination }> {
  const query = new URLSearchParams();
  if (params.tagIds?.length) params.tagIds.forEach((id) => query.append('tagIds', String(id)));
  if (params.questionType) query.set('questionType', params.questionType);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 20));

  const res = await fetch(`${base()}/questions?${query}`, { credentials: 'include' });
  if (!res.ok) throw await parseError(res);
  const body = await res.json();
  return { data: body.data, pagination: body.meta?.pagination };
}

export async function getRandomQuestions(params: {
  tagIds?: number[];
  questionType?: string;
  count?: number;
}): Promise<PracticeQuestion[]> {
  const query = new URLSearchParams();
  if (params.tagIds?.length) params.tagIds.forEach((id) => query.append('tagIds', String(id)));
  if (params.questionType) query.set('questionType', params.questionType);
  query.set('count', String(params.count ?? 1));

  const res = await fetch(`${base()}/questions/random?${query}`, { credentials: 'include' });
  if (!res.ok) throw await parseError(res);
  const body = await res.json();
  return body.data;
}

export async function getTags(category?: string): Promise<PracticeTag[]> {
  const query = category ? `?category=${category}` : '';
  const res = await fetch(`${base()}/tags${query}`, { credentials: 'include' });
  if (!res.ok) throw await parseError(res);
  const body = await res.json();
  return body.data;
}

export async function submitAnswer(
  request: SubmitPracticeAnswerRequest,
): Promise<PracticeSessionResponse> {
  const res = await fetch(`${base()}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(request),
  });
  if (!res.ok) throw await parseError(res);
  const body = await res.json();
  return body.data;
}

export async function getSessions(params: {
  questionType?: string;
  page?: number;
  size?: number;
}): Promise<{ data: PracticeSessionResponse[]; pagination: PracticePagination }> {
  const query = new URLSearchParams();
  if (params.questionType) query.set('questionType', params.questionType);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 20));

  const res = await fetch(`${base()}/sessions?${query}`, { credentials: 'include' });
  if (!res.ok) throw await parseError(res);
  const body = await res.json();
  return { data: body.data, pagination: body.meta?.pagination };
}

export async function getSessionDetail(sessionId: number): Promise<PracticeSessionDetailResponse> {
  const res = await fetch(`${base()}/sessions/${sessionId}`, { credentials: 'include' });
  if (!res.ok) throw await parseError(res);
  const body = await res.json();
  return body.data;
}
