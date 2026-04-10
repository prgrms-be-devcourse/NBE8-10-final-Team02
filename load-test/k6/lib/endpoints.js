// BASE_URL 환경변수로 주입. 기본값은 로컬
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const ENDPOINTS = {
  // ── 헬스 ──────────────────────────────────────────────────────────────
  health: `${BASE_URL}/actuator/health`,

  // ── 인증 (토큰 없이 접근 가능) ─────────────────────────────────────────
  authStatus: `${BASE_URL}/api/v1/auth/status`,

  // ── 사용자 ────────────────────────────────────────────────────────────
  me: `${BASE_URL}/api/v1/users/me`,

  // ── 문서 ──────────────────────────────────────────────────────────────
  documents:       `${BASE_URL}/api/v1/documents`,
  document:        (id) => `${BASE_URL}/api/v1/documents/${id}`,

  // ── GitHub ────────────────────────────────────────────────────────────
  githubRepositories:   `${BASE_URL}/api/v1/github/repositories`,
  analyzeRepository:    (id) => `${BASE_URL}/api/v1/github/repositories/${id}/analyze`,
  repoSyncStatus:       (id) => `${BASE_URL}/api/v1/github/repositories/${id}/sync-status`,

  // ── Application (자소서) ─────────────────────────────────────────────
  applications:           `${BASE_URL}/api/v1/applications`,
  application:            (id) => `${BASE_URL}/api/v1/applications/${id}`,
  applicationQuestions:   (id) => `${BASE_URL}/api/v1/applications/${id}/questions`,
  generateAnswers:        (id) => `${BASE_URL}/api/v1/applications/${id}/questions/generate-answers`,
  generateAnswersStatus:  (id) => `${BASE_URL}/api/v1/applications/${id}/questions/generate-answers/status`,

  // ── CS / 면접 질문 목록 ───────────────────────────────────────────────
  csQuestions: `${BASE_URL}/api/v1/practice/questions`,

  // ── 면접 질문 세트 ────────────────────────────────────────────────────
  questionSets: `${BASE_URL}/api/v1/interview/question-sets`,

  // ── 면접 세션 ─────────────────────────────────────────────────────────
  interviewSessions:              `${BASE_URL}/api/v1/interview/sessions`,
  interviewSession:               (id) => `${BASE_URL}/api/v1/interview/sessions/${id}`,
  interviewSessionAnswers:        (id) => `${BASE_URL}/api/v1/interview/sessions/${id}/answers`,
  interviewSessionComplete:       (id) => `${BASE_URL}/api/v1/interview/sessions/${id}/complete`,
  interviewSessionResult:         (id) => `${BASE_URL}/api/v1/interview/sessions/${id}/result`,

  // ── 내부 관리 (스텁 모드 전환 / 테스트 토큰 발급) ──────────────────────
  loadTestEnable:  `${BASE_URL}/internal/load-test/enable`,
  loadTestDisable: `${BASE_URL}/internal/load-test/disable`,
  loadTestStatus:  `${BASE_URL}/internal/load-test/status`,
  loadTestToken:   `${BASE_URL}/internal/load-test/token`,
};
