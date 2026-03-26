import type {
  Application,
  ApplicationQuestion,
  CreateApplicationRequest,
  GenerateAnswersRequest,
  GenerateAnswersResponse,
  SaveQuestionsRequest,
  SaveSourcesRequest,
  SourceBindingResponse,
} from '@/types/application';

const base = () =>
  (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000') + '/api/v1/applications';

async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body?.error?.message ?? `오류가 발생했습니다. (${res.status})`;
  } catch {
    return `오류가 발생했습니다. (${res.status})`;
  }
}

/**
 * GET /applications
 * 내 지원 준비 목록 조회.
 */
export async function getApplications(): Promise<Application[]> {
  const res = await fetch(base(), {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as Application[];
}

/**
 * POST /applications
 * 새 지원 준비 생성.
 */
export async function createApplication(
  request: CreateApplicationRequest,
): Promise<Application> {
  const res = await fetch(base(), {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as Application;
}

/**
 * DELETE /applications/{applicationId}
 * 지원 준비 삭제.
 */
export async function deleteApplication(applicationId: number): Promise<void> {
  const res = await fetch(`${base()}/${applicationId}`, {
    method: 'DELETE',
    credentials: 'include',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }
}

/**
 * GET /applications/{applicationId}
 * 지원 준비 상세 조회.
 */
export async function getApplication(applicationId: number): Promise<Application> {
  const res = await fetch(`${base()}/${applicationId}`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as Application;
}

/**
 * PUT /applications/{applicationId}/sources
 * 소스(문서/레포) 연결.
 */
export async function saveSources(
  applicationId: number,
  request: SaveSourcesRequest,
): Promise<SourceBindingResponse> {
  const res = await fetch(`${base()}/${applicationId}/sources`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as SourceBindingResponse;
}

/**
 * POST /applications/{applicationId}/questions
 * 문항 저장 (전체 덮어쓰기).
 */
export async function saveQuestions(
  applicationId: number,
  request: SaveQuestionsRequest,
): Promise<ApplicationQuestion[]> {
  const res = await fetch(`${base()}/${applicationId}/questions`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as ApplicationQuestion[];
}

/**
 * GET /applications/{applicationId}/questions
 * 지원서의 문항 목록 조회.
 */
export async function getQuestions(applicationId: number): Promise<ApplicationQuestion[]> {
  const res = await fetch(`${base()}/${applicationId}/questions`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as ApplicationQuestion[];
}

/**
 * POST /applications/{applicationId}/questions/generate-answers
 * AI 자기소개서 답변 생성.
 */
export async function generateAnswers(
  applicationId: number,
  request: GenerateAnswersRequest,
): Promise<GenerateAnswersResponse> {
  const res = await fetch(`${base()}/${applicationId}/questions/generate-answers`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as GenerateAnswersResponse;
}
