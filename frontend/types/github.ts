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

// POST /github/connections 요청 바디
export interface GithubConnectRequest {
  mode: 'oauth' | 'url';
  githubLogin?: string;
  accessToken?: string;
  accessScope?: string;
}