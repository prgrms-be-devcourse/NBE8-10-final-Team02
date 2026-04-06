/**
 * constant-vus.js — AI Stub 집중 부하 시나리오
 *
 * 목적: Stub 지연 하에서 Hikari 커넥션 풀 및 스레드 한계 측정.
 *       각 VU가 자소서 생성 → 면접 질문 생성을 반복.
 *       가상 스레드 ON 환경에서 DB 커넥션 풀 고갈 지점 확인.
 *
 * 실행:
 *   VUS=20 DURATION=3m BASE_URL=http://<IP>:8080 TEST_JWT_TOKEN=<token> ./run.sh constant
 *
 * 주의: AI Stub이 활성화된 load-test profile 서버에서만 실행.
 *       Stub 지연: 자소서 ~17s(Gemini), 면접질문 ~8s(Gemini)
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { getAuthHeaders } from '../lib/auth.js';
import { assertResponse, AI_TIMEOUT } from '../lib/checks.js';

const VUS      = parseInt(__ENV.VUS || '10');
const DURATION = __ENV.DURATION || '3m';

export const options = {
  vus:      VUS,
  duration: DURATION,
  thresholds: {
    'http_req_duration{type:read}':         ['p(95)<1000'],
    'http_req_duration{type:write}':        ['p(95)<2000'],
    'http_req_duration{type:ai-self-intro}': [`p(95)<${AI_TIMEOUT.selfIntro}`],
    'http_req_duration{type:ai-interview}':  [`p(95)<${AI_TIMEOUT.interviewQuestions}`],
    'api_error_rate':                        ['rate<0.05'],
    'http_req_failed':                       ['rate<0.05'],
  },
  // AI 호출은 응답이 느리므로 k6 기본 타임아웃(60s) 초과 방지
  http: {
    timeout: '120s',
  },
};

export default function () {
  const auth = getAuthHeaders();
  if (!auth['Authorization']) {
    console.warn('TEST_JWT_TOKEN 없음 — AI 엔드포인트 테스트 skip');
    sleep(1);
    return;
  }

  // ── Step 1: Application 생성 ────────────────────────────────────────────
  const createRes = http.post(
    ENDPOINTS.applications,
    JSON.stringify({
      applicationTitle: `stub-test-${__VU}-${Date.now()}`,
      companyName: '테스트기업',
      jobRole: 'backend',
      applicationType: 'full_time',
    }),
    { headers: auth, tags: { type: 'write' } }
  );

  if (!assertResponse(createRes, [200, 201], 2000)) {
    sleep(1);
    return;
  }

  const appId = JSON.parse(createRes.body).data?.id;
  if (!appId) {
    sleep(1);
    return;
  }

  // ── Step 2: 자소서 문항 등록 ────────────────────────────────────────────
  const questionsRes = http.post(
    ENDPOINTS.applicationQuestions(appId),
    JSON.stringify({
      questions: [
        {
          questionOrder: 1,
          questionText: '본인의 강점과 약점을 말씀해 주세요.',
          toneOption: 'formal',
          lengthOption: 'medium',
          emphasisPoint: '협업 경험',
        },
        {
          questionOrder: 2,
          questionText: '지원 동기를 말씀해 주세요.',
          toneOption: 'formal',
          lengthOption: 'long',
          emphasisPoint: null,
        },
      ],
    }),
    { headers: auth, tags: { type: 'write' } }
  );
  assertResponse(questionsRes, [200, 201], 2000);

  // ── Step 3: 자소서 AI 생성 (Stub: ~17s) ────────────────────────────────
  const selfIntroRes = http.post(
    ENDPOINTS.generateAnswers(appId),
    JSON.stringify({ regenerate: false }),
    { headers: auth, tags: { type: 'ai-self-intro' }, timeout: '120s' }
  );
  assertResponse(selfIntroRes, [200], AI_TIMEOUT.selfIntro);

  // ── Step 4: 면접 질문 AI 생성 (Stub: ~8s) ──────────────────────────────
  const interviewRes = http.post(
    ENDPOINTS.questionSets,
    JSON.stringify({
      applicationId: appId,
      title: `stub-test-set-${__VU}`,
      questionCount: 5,
      difficultyLevel: 'medium',
      questionTypes: ['technical_cs', 'behavioral'],
    }),
    { headers: auth, tags: { type: 'ai-interview' }, timeout: '120s' }
  );
  assertResponse(interviewRes, [200, 201], AI_TIMEOUT.interviewQuestions);

  // ── Step 5: Cleanup ─────────────────────────────────────────────────────
  http.del(ENDPOINTS.application(appId), null, {
    headers: auth,
    tags: { type: 'write' },
  });

  // AI 호출이 길어서 sleep 최소화
  sleep(0.5);
}
