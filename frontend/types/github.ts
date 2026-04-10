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
  hasCommits: boolean; // 커밋 동기화 완료 여부
  analysisStatus: RepoSyncStatus | null; // 분석 상태 (미요청 또는 TTL 만료 시 null)
  hasSummary: boolean; // DB에 RepoSummary 레코드 존재 여부 (Redis TTL과 무관한 영구 상태)
  pushedAt: string | null; // GitHub pushed_at (ISO-8601). 기여/URL 추가 경로는 null
  ownerType: 'owner' | 'collaborator' | null; // 목록 조회 외 경로는 null
  language: string | null; // primary language
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
  repositoryId: number | null;  // github_repositories.id (저장된 경우에만 non-null)
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
  step: 'significance_check' | 'clone' | 'analysis' | 'ai_pending' | 'summary' | null;
  startedAt: string | null;
  estimatedEndAt: string | null;
  completedAt: string | null;
  error: string | null;
  skipReason: string | null;
}

// GET /github/repositories/{id}/summary 응답 (nullable — 분석 미완료 시 null)
export interface RepoSummaryResponse {
  repositoryId: number;
  summaryVersion: number;
  data: string;      // portfolio-summary.schema.json 형식 JSON 문자열. JSON.parse() 후 RepoSummaryData로 캐스팅
  generatedAt: string;
}

// RepoSummaryResponse.data를 JSON.parse()한 결과 형태
export interface RepoSummaryData {
  project: {
    projectKey: string;
    projectName: string;
    summary: string;
    role: string | null;
    stack: string[];
    signals: string[];
    evidenceBullets: { fact: string; challengeRef: string | null }[];
    challenges: { id: string; what: string; how: string; learning: string }[];
    techDecisions: { decision: string; reason: string; tradeOff: string | null }[];
    strengths: string[];
    risks: string[];
    sourceRefs: string[];
    qualityFlags: string[];
  };
}