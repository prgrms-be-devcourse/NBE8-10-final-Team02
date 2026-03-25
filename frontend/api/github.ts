import type {
  GithubConnection,
  GithubConnectRequest,
  GithubRepository,
  RepositorySelectionResponse,
  RepositorySyncResponse,
  RepoSyncStatus,
  ContributedRepo,
  SaveContributionRequest,
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
 * GitHub OAuth 연결 생성 또는 갱신.
 * mode=oauth: accessToken 필수. Google/Kakao 로그인 사용자도 GitHub 기능 사용 시 필요.
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

/**
 * POST /github/repositories/{repositoryId}/analyze
 * 분석 파이프라인(clone → 정적 분석 → AI 요약) 시작. 202 Accepted 반환.
 * 커밋 동기화(sync-commits) 완료 후 호출해야 한다.
 */
export async function analyzeRepository(repositoryId: number): Promise<RepoSyncStatus | null> {
  const res = await fetch(`${base()}/repositories/${repositoryId}/analyze`, {
    method: 'POST',
    credentials: 'include',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return (body.data as RepoSyncStatus) ?? null;
}

/**
 * GET /github/repositories/{repositoryId}/sync-status
 * 분석 파이프라인 진행 상태 조회.
 * 상태가 없으면(미요청 또는 TTL 만료) null 반환.
 */
export async function getAnalysisStatus(repositoryId: number): Promise<RepoSyncStatus | null> {
  const res = await fetch(`${base()}/repositories/${repositoryId}/sync-status`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return (body.data as RepoSyncStatus) ?? null;
}

/**
 * DELETE /github/repositories/{repositoryId}/analyze
 * 진행 중인 분석 파이프라인을 취소한다. 이미 완료됐어도 200을 반환한다 (idempotent).
 */
export async function cancelAnalysis(repositoryId: number): Promise<void> {
  const res = await fetch(`${base()}/repositories/${repositoryId}/analyze`, {
    method: 'DELETE',
    credentials: 'include',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }
}

/**
 * GET /github/contributions/discovered?yearsOffset={n}
 * 사용자가 커밋을 기여한 public repo 목록 조회.
 * yearsOffset: 0=최근 2년, 1=2~4년 전, 2=4~6년 전
 */
export async function getContributions(yearsOffset: number): Promise<ContributedRepo[]> {
  const res = await fetch(`${base()}/contributions/discovered?yearsOffset=${yearsOffset}`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as ContributedRepo[];
}

/**
 * POST /github/contributions/save
 * 기여 탐색 목록에서 선택한 repo를 저장한다.
 * 이미 저장된 경우 기존 레코드를 반환한다.
 */
export async function saveContribution(request: SaveContributionRequest): Promise<GithubRepository> {
  const res = await fetch(`${base()}/contributions/save`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as GithubRepository;
}

/**
 * POST /github/contributions/add-by-url
 * 사용자가 직접 입력한 GitHub URL로 기여 repo를 추가한다.
 * 본인 커밋이 없으면 422 에러를 반환한다.
 */
export async function addContributionByUrl(url: string): Promise<GithubRepository> {
  const res = await fetch(`${base()}/contributions/add-by-url`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as GithubRepository;
}