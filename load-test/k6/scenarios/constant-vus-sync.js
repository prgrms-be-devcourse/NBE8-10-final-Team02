/**
 * constant-vus.js — AI Stub 집중 부하 시나리오
 *
 * 목적: Stub 지연 하에서 Hikari 커넥션 풀 및 스레드 한계 측정.
 *       각 VU가 자소서 생성 → 면접 질문 생성을 반복.
 *       가상 스레드 ON 환경에서 DB 커넥션 풀 고갈 지점 확인.
 *
 * 실행:
 *   VUS=20 DURATION=3m BASE_URL=http://<IP>:8080 TEST_JWT_TOKEN=<token> TEST_API_KEY=<apiKey> ./run.sh constant
 *
 * 주의: AI Stub이 활성화된 load-test profile 서버에서만 실행.
 *       Stub 지연: 자소서 ~17s(Gemini), 면접질문 ~8s(Gemini)
 *
 * 자소서 AI 생성 흐름 (202 + polling):
 *   POST generate-answers → 202 Accepted (즉시 반환)
 *   GET  generate-answers/status 폴링 → COMPLETED/FAILED 확인
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { acquireToken, getAuthHeaders } from '../lib/auth.js';
import { assertResponse, AI_TIMEOUT } from '../lib/checks.js';

const VUS      = parseInt(__ENV.VUS || '10');
const DURATION = __ENV.DURATION || '3m';

// 자소서 폴링 설정
const POLL_INTERVAL_S   = 2;       // 폴링 간격 (초)
const POLL_MAX_WAIT_S   = 60;      // 최대 대기 (초) — Stub ~17s 기준 여유 있게
const POLL_MAX_ATTEMPTS = Math.ceil(POLL_MAX_WAIT_S / POLL_INTERVAL_S);

export const options = {
  vus:      VUS,
  duration: DURATION,
  thresholds: {
    'http_req_duration{type:read}':      ['p(95)<1000'],
    'http_req_duration{type:write}':     ['p(95)<2000'],
    'http_req_duration{type:ai-accept}': ['p(95)<500'],   // 202 즉시 반환 기준
    'http_req_duration{type:ai-poll}':   ['p(95)<300'],   // 폴링 조회 기준
    'http_req_duration{type:ai-interview}': [`p(95)<${AI_TIMEOUT.interviewQuestions}`],
    'api_error_rate':                    ['rate<0.05'],
    'http_req_failed':                   ['rate<0.05'],
  },
  // url을 systemTags에서 제외 → 동적 ID가 Prometheus 레이블로 올라가지 않아 high cardinality 방지
  systemTags: ['status', 'method', 'name', 'check', 'error', 'error_code', 'scenario'],
  http: {
    timeout: '30s',  // 각 요청 타임아웃 (폴링은 짧게, AI 직접 호출 없으므로 30s로 충분)
  },
};

export function setup() {
  return acquireToken(); // { token, apiKey }
}

export default function ({ token, apiKey }) {
  const headers = getAuthHeaders({ token, apiKey });
  if (!headers['Authorization']) {
    console.warn('인증 정보 없음 — AI 엔드포인트 테스트 skip');
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
    { headers: headers, tags: { type: 'write' } }
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
    { headers: headers, tags: { type: 'write', name: 'post_application_questions' } }
  );
  if (!assertResponse(questionsRes, [200, 201], 2000)) {
    console.error(`[VU${__VU}] Step2 문항등록 실패: ${questionsRes.status} ${questionsRes.body}`);
    sleep(1);
    return;
  }

  // ── Step 3: 자소서 AI 생성 (202 즉시 반환 + 폴링) ──────────────────────
  const selfIntroRes = http.post(
    ENDPOINTS.generateAnswers(appId),
    JSON.stringify({ useTemplate: true, regenerate: false }),
    { headers: headers, tags: { type: 'ai-accept', name: 'generate_answers_submit' } }
  );
  if (!assertResponse(selfIntroRes, [202], 500)) {
    console.error(`[VU${__VU}] Step3 자소서생성 제출 실패: ${selfIntroRes.status} ${selfIntroRes.body}`);
    // 제출 실패해도 면접 질문 생성은 시도
  } else {
    // 폴링: COMPLETED 또는 FAILED까지 대기
    let attempts = 0;
    let completed = false;

    while (attempts < POLL_MAX_ATTEMPTS) {
      sleep(POLL_INTERVAL_S);
      attempts++;

      const pollRes = http.get(
        ENDPOINTS.generateAnswersStatus(appId),
        { headers: headers, tags: { type: 'ai-poll', name: 'generate_answers_status' } }
      );

      if (!assertResponse(pollRes, [200], 300)) {
        continue; // 일시 오류 → 재시도
      }

      let jobData = null;
      try {
        jobData = JSON.parse(pollRes.body).data;
      } catch {
        continue;
      }

      if (!jobData) continue;

      if (jobData.status === 'COMPLETED') {
        completed = true;
        break;
      }

      if (jobData.status === 'FAILED') {
        console.error(`[VU${__VU}] Step3 자소서생성 실패(FAILED): ${jobData.error}`);
        break;
      }

      // PENDING / IN_PROGRESS → 계속 폴링
    }

    if (!completed && attempts >= POLL_MAX_ATTEMPTS) {
      console.warn(`[VU${__VU}] Step3 자소서생성 폴링 타임아웃 (${POLL_MAX_WAIT_S}s 초과)`);
    }
  }

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
    { headers: headers, tags: { type: 'ai-interview', name: 'post_question_sets' }, timeout: '120s' }
  );
  assertResponse(interviewRes, [200, 201], AI_TIMEOUT.interviewQuestions);

  // ── Step 5: Cleanup ─────────────────────────────────────────────────────
  http.del(ENDPOINTS.application(appId), null, {
    headers: headers,
    tags: { type: 'write', name: 'delete_application' },
  });

  sleep(0.5);
}
