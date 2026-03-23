import type {
  GithubConnection,
  GithubConnectRequest,
  GithubRepository,
  RepositorySelectionResponse,
  RepositorySyncResponse,
} from '@/types/github';
import type { Pagination } from '@/types/common';

const base = () =>
  (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000') + '/api/v1/github';

// 공통 에러 파싱 — 백엔드 표준 에러 응답에서 메시지를 꺼낸다
async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body?.error?.message ?? `오류가 발생했습니다. (${res.status})`;
  } catch {
    return `오류가 발생했습니다. (${res.status})`;
  }
}

/**
 * GET /github/connections
 * 현재 사용자의 GitHub 연결 정보 조회.
 * 연결이 없으면 null 반환 (에러 아님).
 */
export async function getGithubConnection(): Promise<GithubConnection | null> {
  const res = await fetch(`${base()}/connections`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return (body.data as GithubConnection) ?? null;
}

/**
 * POST /github/connections
 * GitHub 연결 생성 또는 갱신.
 * mode=url: githubLogin 필수
 * mode=oauth: accessToken 필수
 */
export async function connectGithub(
  request: GithubConnectRequest,
): Promise<GithubConnection> {
  const res = await fetch(`${base()}/connections`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as GithubConnection;
}

/**
 * GET /github/repositories
 * 저장된 repo 목록 조회.
 * selected: undefined → 전체 / true → 선택된 것만 / false → 미선택만
 */
export async function getRepositories(params?: {
  selected?: boolean;
  page?: number;
  size?: number;
}): Promise<{ data: GithubRepository[]; pagination: Pagination }> {
  const query = new URLSearchParams();
  if (params?.selected !== undefined) query.set('selected', String(params.selected));
  if (params?.page) query.set('page', String(params.page));
  if (params?.size) query.set('size', String(params.size));

  const res = await fetch(`${base()}/repositories?${query}`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return {
    data: body.data as GithubRepository[],
    pagination: body.meta?.pagination as Pagination,
  };
}

/**
 * PUT /github/repositories/selection
 * 선택할 repo ID 목록 저장. 빈 배열이면 전체 해제.
 */
export async function saveRepositorySelection(
  repositoryIds: number[],
): Promise<RepositorySelectionResponse> {
  const res = await fetch(`${base()}/repositories/selection`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ repositoryIds }),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as RepositorySelectionResponse;
}

/**
 * POST /github/connections/refresh
 * GitHub OAuth 로그인 사용자의 저장된 token으로 repo 목록을 갱신한다.
 * GitHub OAuth로 로그인 후 처음 repo 목록을 가져올 때 사용한다.
 */
export async function refreshGithubConnection(): Promise<GithubConnection> {
  const res = await fetch(`${base()}/connections/refresh`, {
    method: 'POST',
    credentials: 'include',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as GithubConnection;
}

/**
 * POST /github/repositories/{repositoryId}/sync-commits
 * 선택한 repo의 커밋 동기화 시작. 202 Accepted 반환.
 */
export async function syncCommits(repositoryId: number): Promise<RepositorySyncResponse> {
  const res = await fetch(`${base()}/repositories/${repositoryId}/sync-commits`, {
    method: 'POST',
    credentials: 'include',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as RepositorySyncResponse;
}