/**
 * constant-vus.js — AI Stub 집중 부하 시나리오
 *
 * 목적: Stub 지연 하에서 Hikari 커넥션 풀 및 세마포어 병목 측정.
 *       각 VU가 자소서 생성 → 면접 질문 생성을 반복.
 *       가상 스레드 ON 환경에서 세마포어 고갈 지점 확인.
 *
 * 실행:
 *   VUS=20 DURATION=3m BASE_URL=http://<IP>:8080 TEST_JWT_TOKEN=<token> TEST_API_KEY=<apiKey> ./run.sh constant
 *
 * 주의: AI Stub이 활성화된 load-test profile 서버에서만 실행.
 *       Stub 지연: 자소서 ~17s(Gemini), 면접질문 ~8s(Gemini)
 *
 * 비동기 AI 생성 흐름 (202 + polling):
 *   POST generate-answers          → 202 Accepted (즉시 반환)
 *   GET  generate-answers/status   → COMPLETED/FAILED 확인
 *   POST question-sets             → 202 Accepted (즉시 반환)
 *   GET  question-sets/status/{jobId} → COMPLETED/FAILED 확인
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { acquireToken, getAuthHeaders } from '../lib/auth.js';
import { assertResponse, AI_TIMEOUT } from '../lib/checks.js';

const VUS      = parseInt(__ENV.VUS || '10');
const DURATION = __ENV.DURATION || '3m';

// 폴링 설정
const POLL_INTERVAL_S = 2;

// 자소서: Stub ~17s 기준
const SELF_INTRO_POLL_MAX_WAIT_S   = 180;
const SELF_INTRO_POLL_MAX_ATTEMPTS = Math.ceil(SELF_INTRO_POLL_MAX_WAIT_S / POLL_INTERVAL_S);

// 면접 질문: Stub ~8s 기준 + 세마포어 대기 포함
const QS_POLL_MAX_WAIT_S   = 240;
const QS_POLL_MAX_ATTEMPTS = Math.ceil(QS_POLL_MAX_WAIT_S / POLL_INTERVAL_S);

export const options = {
  vus:          VUS,
  duration:     DURATION,
  gracefulStop: '60s',
  thresholds: {
    'http_req_duration{type:write}':      ['p(95)<2000'],
    'http_req_duration{type:ai-accept}':  ['p(95)<95000'], // 세마포어 대기(최대 90s) 포함
    'api_error_rate':                     ['rate<0.05'],
    'http_req_failed':                    ['rate<0.05'],
  },
  systemTags: ['status', 'method', 'name', 'url', 'expected_response', 'check', 'error', 'error_code', 'scenario'],
  http: {
    timeout: '30s',
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
  } else {
    pollUntilDone(
      () => http.get(ENDPOINTS.generateAnswersStatus(appId), { headers: headers, tags: { type: 'ai-poll', name: 'generate_answers_status' } }),
      SELF_INTRO_POLL_MAX_ATTEMPTS,
      `Step3 자소서생성`
    );
  }

  // ── Step 4: 면접 질문 AI 생성 (202 즉시 반환 + 폴링) ───────────────────
  const qsSubmitRes = http.post(
    ENDPOINTS.questionSets,
    JSON.stringify({
      applicationId: appId,
      title: `stub-test-set-${__VU}`,
      questionCount: 5,
      difficultyLevel: 'medium',
      questionTypes: ['technical_cs', 'behavioral'],
    }),
    { headers: headers, tags: { type: 'ai-accept', name: 'post_question_sets_submit' } }
  );

  if (!assertResponse(qsSubmitRes, [202], 500)) {
    console.error(`[VU${__VU}] Step4 면접질문생성 제출 실패: ${qsSubmitRes.status} ${qsSubmitRes.body}`);
  } else {
    let jobId = null;
    try {
      jobId = JSON.parse(qsSubmitRes.body).data?.jobId;
    } catch { /* ignore */ }

    if (jobId) {
      pollUntilDone(
        () => http.get(ENDPOINTS.questionSetJobStatus(jobId), { headers: headers, tags: { type: 'ai-poll', name: 'question_set_status' } }),
        QS_POLL_MAX_ATTEMPTS,
        `Step4 면접질문생성`
      );
    }
  }

  // ── Step 5: Cleanup ─────────────────────────────────────────────────────
  http.del(ENDPOINTS.application(appId), null, {
    headers: headers,
    tags: { type: 'write', name: 'delete_application' },
  });

  sleep(0.5);
}

/**
 * COMPLETED 또는 FAILED가 될 때까지 폴링한다.
 * @param {Function} pollFn  - 호출 시 http response를 반환하는 함수
 * @param {number}   maxAttempts
 * @param {string}   label   - 로그용 라벨
 */
function pollUntilDone(pollFn, maxAttempts, label) {
  let attempts = 0;
  while (attempts < maxAttempts) {
    sleep(POLL_INTERVAL_S);
    attempts++;

    const pollRes = pollFn();
    if (pollRes.status !== 200) continue;

    let data = null;
    try { data = JSON.parse(pollRes.body).data; } catch { continue; }
    if (!data) continue;

    if (data.status === 'COMPLETED') return;
    if (data.status === 'FAILED') {
      console.error(`[VU${__VU}] ${label} FAILED: ${data.error}`);
      return;
    }
  }
  console.warn(`[VU${__VU}] ${label} 폴링 타임아웃 (${maxAttempts * POLL_INTERVAL_S}s 초과)`);
}
