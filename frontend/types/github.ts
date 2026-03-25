// 백엔드 GithubConnectionResponse와 1:1 대응
export interface GithubConnection {
  id: number;
  userId: number;
  githubUserId: number;
  githubLogin: string;
  accessScope: string | null;
  syncStatus: 'pending' | 'success' | 'failed';
  connectedAt: string;
  lastSyncedAt: string | null;
}

// 백엔드 GithubRepositoryResponse와 1:1 대응
export interface GithubRepository {
  id: number;
  githubRepoId: number;
  ownerLogin: string;
  repoName: string;
  fullName: string;
  htmlUrl: string;
  visibility: 'public' | 'private' | 'internal';
  defaultBranch: string | null;
  isSelected: boolean;
}

export interface RepositorySelectionResponse {
  selectedRepositoryIds: number[];
  selectedCount: number;
}

export interface RepositorySyncResponse {
  repositoryId: number;
  syncStatus: string;
  queuedAt: string;
}

// POST /github/connections 요청 바디 — mode는 'oauth'만 허용
export interface GithubConnectRequest {
  mode: 'oauth';
  accessToken: string;
  accessScope?: string;
}

// 백엔드 ContributedRepoResponse와 1:1 대응
export interface ContributedRepo {
  githubRepoId: number;
  nameWithOwner: string;
  url: string;
  language: string | null;
  repoSizeKb: number | null;
  contributionCount: number;
  alreadySaved: boolean;
}

// POST /github/contributions/save 요청 바디
export interface SaveContributionRequest {
  githubRepoId: number;
  nameWithOwner: string;
  url: string;
  language: string | null;
  repoSizeKb: number | null;
}

// GET /github/repositories/{id}/sync-status 응답 (nullable)
export interface RepoSyncStatus {
  repositoryId: number;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'FAILED';
  step: 'significance_check' | 'clone' | 'analysis' | 'summary' | null;
  startedAt: string | null;
  estimatedEndAt: string | null;
  completedAt: string | null;
  error: string | null;
}