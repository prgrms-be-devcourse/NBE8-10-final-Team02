// BASE_URL 환경변수로 주입. 기본값은 로컬
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const ENDPOINTS = {
  // ── 헬스 ──────────────────────────────────────────────────────────────
  health: `${BASE_URL}/actuator/health`,

  // ── 인증 (토큰 없이 접근 가능) ─────────────────────────────────────────
  authStatus: `${BASE_URL}/api/v1/auth/status`,

  // ── CS / 면접 질문 목록 (공개 or 인증 필요) ──────────────────────────────
  csQuestions:         `${BASE_URL}/api/v1/interview/cs-questions`,
  interviewQuestions:  (setId) => `${BASE_URL}/api/v1/interview-question-sets/${setId}/questions`,

  // ── 사용자 ────────────────────────────────────────────────────────────
  me: `${BASE_URL}/api/v1/users/me`,

  // ── Application (자소서) ─────────────────────────────────────────────
  applications:           `${BASE_URL}/api/v1/applications`,
  application:            (id) => `${BASE_URL}/api/v1/applications/${id}`,
  applicationQuestions:   (id) => `${BASE_URL}/api/v1/applications/${id}/questions`,
  generateAnswers:        (id) => `${BASE_URL}/api/v1/applications/${id}/questions/generate-answers`,

  // ── 면접 질문 세트 ────────────────────────────────────────────────────
  questionSets:       `${BASE_URL}/api/v1/interview/question-sets`,

  // ── 면접 세션 ─────────────────────────────────────────────────────────
  sessions:           `${BASE_URL}/api/v1/interview-sessions`,
  session:            (id) => `${BASE_URL}/api/v1/interview-sessions/${id}`,
  sessionAnswer:      (id) => `${BASE_URL}/api/v1/interview-sessions/${id}/answers`,

  // ── 내부 관리 (스텁 모드 전환) ────────────────────────────────────────
  loadTestEnable:  `${BASE_URL}/internal/load-test/enable`,
  loadTestDisable: `${BASE_URL}/internal/load-test/disable`,
  loadTestStatus:  `${BASE_URL}/internal/load-test/status`,
};
