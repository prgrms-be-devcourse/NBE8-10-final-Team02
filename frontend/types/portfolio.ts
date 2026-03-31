export type PortfolioReadinessMetricStatus = 'ready' | 'not_ready';
export type PortfolioConnectionStatus = 'connected' | 'not_connected';
export type PortfolioScopeStatus =
  | 'not_applicable'
  | 'public_only'
  | 'private_ready'
  | 'insufficient';
export type PortfolioMissingItem =
  | 'github_connection'
  | 'selected_repository'
  | 'document_source'
  | 'document_extract_success';
export type PortfolioNextRecommendedAction =
  | 'connect_github'
  | 'select_repository'
  | 'upload_document'
  | 'retry_document_extraction'
  | 'start_application';

export interface PortfolioReadinessCountMetric {
  status: PortfolioReadinessMetricStatus;
  value: number | null;
}

export interface PortfolioReadinessProfile {
  userId: number;
  displayName: string;
  email: string | null;
  profileImageUrl: string | null;
}

export interface PortfolioReadinessGithub {
  connectionStatus: PortfolioConnectionStatus;
  scopeStatus: PortfolioScopeStatus;
  selectedRepositoryCount: number;
  recentCollectedCommitCount: PortfolioReadinessCountMetric;
}

export interface PortfolioReadinessDocuments {
  totalCount: number;
  extractSuccessCount: number;
  extractFailedCount: number;
}

export interface PortfolioReadinessReadiness {
  missingItems: PortfolioMissingItem[];
  nextRecommendedAction: PortfolioNextRecommendedAction;
  canStartApplication: boolean;
}

export interface PortfolioReadinessAlertItem {
  code: string;
  message: string;
  occurredAt: string;
}

export interface PortfolioReadinessRecentFailedJobs {
  status: PortfolioReadinessMetricStatus;
  items: PortfolioReadinessAlertItem[] | null;
}

export interface PortfolioReadinessAlerts {
  recentFailedJobs: PortfolioReadinessRecentFailedJobs;
}

export interface PortfolioReadinessDashboard {
  profile: PortfolioReadinessProfile;
  github: PortfolioReadinessGithub;
  documents: PortfolioReadinessDocuments;
  readiness: PortfolioReadinessReadiness;
  alerts: PortfolioReadinessAlerts;
}
