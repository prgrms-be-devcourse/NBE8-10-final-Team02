/**
 * interview-session.js — 면접 세션 전체 흐름 부하 시나리오
 *
 * 목적: CS/인성 면접 답변 제출 → 세션 완료 → AI 결과 리포트 생성까지
 *       여러 사용자가 동시에 진행할 때 AI 처리 한계 및 DB 커넥션 풀 측정.
 *
 * 흐름:
 *   setup()  → Application + QuestionSet 준비 (AI 1회 호출)
 *   default  → 세션 시작 → 질문 조회 → 답변 제출 × N → 완료 → 결과 조회
 *   teardown → setup에서 만든 Application 삭제
 *
 * 주의: AI Stub이 활성화된 load-test profile 서버에서 실행 권장.
 *
 * 실행:
 *   VUS=10 DURATION=3m BASE_URL=http://13.125.255.89:8080 ./run.sh interview-session
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { acquireToken, getAuthHeaders } from '../lib/auth.js';
import { assertResponse, AI_TIMEOUT } from '../lib/checks.js';

const MAX_VUS      = parseInt(__ENV.VUS || '5');
const QUESTION_COUNT = 5;

export const options = {
  vus:      MAX_VUS,
  duration: __ENV.DURATION || '3m',
  thresholds: {
    'http_req_duration{type:start-session}':    ['p(95)<3000'],
    'http_req_duration{type:submit-answer}':    ['p(95)<3000'],
    'http_req_duration{type:complete-session}': [`p(95)<${AI_TIMEOUT.evaluate}`],
    'http_req_duration{type:get-result}':       ['p(95)<5000'],
    'api_error_rate':  ['rate<0.05'],
    'http_req_failed': ['rate<0.05'],
  },
  http: { timeout: '120s' },
};

export function setup() {
  const token = acquireToken();
  const auth  = getAuthHeaders(token);

  // ── 1. Application 생성 ─────────────────────────────────────────────
  const appRes = http.post(
    ENDPOINTS.applications,
    JSON.stringify({
      applicationTitle: 'load-test-interview-setup',
      companyName:      '테스트기업',
      jobRole:          'backend',
      applicationType:  'full_time',
    }),
    { headers: auth }
  );
  if (!assertResponse(appRes, [200, 201], 3000)) {
    throw new Error(`Application 생성 실패: ${appRes.status} ${appRes.body}`);
  }
  const appId = appRes.json('data.id');

  // ── 2. 자소서 문항 등록 ─────────────────────────────────────────────
  http.post(
    ENDPOINTS.applicationQuestions(appId),
    JSON.stringify({
      questions: [
        {
          questionOrder: 1,
          questionText:  '자신의 강점과 약점을 설명해 주세요.',
          toneOption:    'formal',
          lengthOption:  'medium',
          emphasisPoint: '협업 경험',
        },
      ],
    }),
    { headers: auth }
  );

  // ── 3. 면접 질문 세트 생성 (AI 1회) ─────────────────────────────────
  const qsRes = http.post(
    ENDPOINTS.questionSets,
    JSON.stringify({
      applicationId:  appId,
      title:          'load-test-question-set',
      questionCount:  QUESTION_COUNT,
      difficultyLevel: 'medium',
      questionTypes:  ['technical_cs', 'behavioral'],
    }),
    { headers: auth, timeout: '120s' }
  );
  if (!assertResponse(qsRes, [200, 201], AI_TIMEOUT.interviewQuestions)) {
    throw new Error(`질문 세트 생성 실패: ${qsRes.status} ${qsRes.body}`);
  }
  const questionSetId = qsRes.json('data.id');

  console.log(`[setup] appId=${appId}, questionSetId=${questionSetId}`);
  return { token, questionSetId, appId };
}

export default function ({ token, questionSetId }) {
  const auth = getAuthHeaders(token);

  // ── 세션 시작 ───────────────────────────────────────────────────────
  const sessionRes = http.post(
    ENDPOINTS.interviewSessions,
    JSON.stringify({ questionSetId }),
    { headers: auth, tags: { type: 'start-session' } }
  );
  if (!assertResponse(sessionRes, [200, 201], 3000)) {
    sleep(1);
    return;
  }
  const sessionId = sessionRes.json('data.id');

  // ── 세션 상세 조회 (질문 목록 확보) ────────────────────────────────
  const detailRes = http.get(
    ENDPOINTS.interviewSession(sessionId),
    { headers: auth, tags: { type: 'get-session-detail' } }
  );
  if (!assertResponse(detailRes, [200], 2000)) {
    sleep(1);
    return;
  }
  const questions = detailRes.json('data.questions') || [];

  // ── 각 질문에 순차 답변 제출 ────────────────────────────────────────
  for (let i = 0; i < questions.length; i++) {
    const q = questions[i];
    http.post(
      ENDPOINTS.interviewSessionAnswers(sessionId),
      JSON.stringify({
        questionId:  q.id,
        answerOrder: i + 1,
        answerText:  `부하테스트 답변입니다. ${i + 1}번 질문에 대한 답변으로, ` +
                     '충분한 길이의 답변 텍스트를 포함합니다. '.repeat(8),
        isSkipped:   false,
      }),
      { headers: auth, tags: { type: 'submit-answer' } }
    );
    sleep(0.2);
  }

  // ── 세션 완료 → AI 평가 트리거 ─────────────────────────────────────
  const completeRes = http.post(
    ENDPOINTS.interviewSessionComplete(sessionId),
    null,
    { headers: auth, tags: { type: 'complete-session' }, timeout: '120s' }
  );
  assertResponse(completeRes, [200], AI_TIMEOUT.evaluate);

  // ── 결과 리포트 조회 ────────────────────────────────────────────────
  const resultRes = http.get(
    ENDPOINTS.interviewSessionResult(sessionId),
    { headers: auth, tags: { type: 'get-result' } }
  );
  assertResponse(resultRes, [200], 5000);

  sleep(0.5);
}

export function teardown({ token, appId }) {
  if (!appId) return;
  const auth = getAuthHeaders(token);
  http.del(ENDPOINTS.application(appId), null, { headers: auth });
  console.log(`[teardown] appId=${appId} 삭제 완료`);
}
