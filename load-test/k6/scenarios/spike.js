/**
 * spike.js — AI Stub 순간 폭발 시나리오 (async 202+polling 버전)
 *
 * 목적: 동시 AI 요청이 급증할 때 세마포어 포화 속도, 에러율 임계점 측정.
 *       generateAnswers 202 수락 → polling 완료까지 추적.
 *
 * 실행:
 *   VUS=50 BASE_URL=http://<IP>:8080 ./bench-compare.sh test-one final-sema-20
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { acquireToken, getAuthHeaders } from '../lib/auth.js';
import { assertResponse } from '../lib/checks.js';

const MAX_VUS = parseInt(__ENV.VUS || '50');

// 폴링 설정 — stub ~17s + 세마포어 대기 포함
const POLL_INTERVAL_S    = 2;
const POLL_MAX_WAIT_S    = 180;
const POLL_MAX_ATTEMPTS  = Math.ceil(POLL_MAX_WAIT_S / POLL_INTERVAL_S);

export const options = {
  stages: [
    { duration: '10s',  target: 0        },  // 대기 (서버 안정화)
    { duration: '10s',  target: MAX_VUS  },  // 급격한 스파이크
    { duration: '120s', target: MAX_VUS  },  // 최고 부하 유지 (세마포어 포화 관찰)
    { duration: '10s',  target: 0        },  // 급격한 감소
    { duration: '60s',  target: 0        },  // 회복 — 잔여 태스크 완료 대기
  ],
  gracefulStop: '60s',
  thresholds: {
    'http_req_duration{type:ai-accept}': ['p(95)<95000'],  // 세마포어 대기 포함
    'api_error_rate':                    ['rate<0.20'],
    'http_req_failed':                   ['rate<0.20'],
  },
  systemTags: ['status', 'method', 'name', 'url', 'expected_response', 'check', 'error', 'error_code', 'scenario'],
  http: {
    timeout: '30s',
  },
};

export function setup() {
  return acquireToken();
}

export default function ({ token, apiKey }) {
  const headers = getAuthHeaders({ token, apiKey });
  if (!headers['Authorization']) {
    sleep(1);
    return;
  }

  // ── Step 1: Application 생성 ────────────────────────────────────────────
  const createRes = http.post(
    ENDPOINTS.applications,
    JSON.stringify({
      applicationTitle: `spike-${__VU}-${Date.now()}`,
      companyName: '스파이크기업',
      jobRole: 'backend',
      applicationType: 'full_time',
    }),
    { headers: headers, tags: { type: 'write' } }
  );

  if (!assertResponse(createRes, [200, 201], 2000)) {
    sleep(0.5);
    return;
  }

  const appId = JSON.parse(createRes.body).data?.id;
  if (!appId) { sleep(0.5); return; }

  // ── Step 2: 문항 등록 ───────────────────────────────────────────────────
  http.post(
    ENDPOINTS.applicationQuestions(appId),
    JSON.stringify({
      questions: [{
        questionOrder:  1,
        questionText:   '자신을 소개해 주세요.',
        toneOption:     'formal',
        lengthOption:   'short',
        emphasisPoint:  null,
      }],
    }),
    { headers: headers, tags: { type: 'write', name: 'post_application_questions' } }
  );

  // ── Step 3: 자소서 AI 생성 (202 즉시 반환 + 폴링) ──────────────────────
  const aiRes = http.post(
    ENDPOINTS.generateAnswers(appId),
    JSON.stringify({ useTemplate: true, regenerate: false }),
    { headers: headers, tags: { type: 'ai-accept', name: 'generate_answers_submit' } }
  );

  if (assertResponse(aiRes, [202], 500)) {
    let attempts = 0;
    while (attempts < POLL_MAX_ATTEMPTS) {
      sleep(POLL_INTERVAL_S);
      attempts++;
      const pollRes = http.get(
        ENDPOINTS.generateAnswersStatus(appId),
        { headers: headers, tags: { type: 'ai-poll', name: 'generate_answers_status' } }
      );
      if (pollRes.status !== 200) continue;
      let data = null;
      try { data = JSON.parse(pollRes.body).data; } catch { continue; }
      if (!data) continue;
      if (data.status === 'COMPLETED') break;
      if (data.status === 'FAILED') {
        console.error(`[VU${__VU}] 자소서 생성 FAILED`);
        break;
      }
    }
    if (attempts >= POLL_MAX_ATTEMPTS) {
      console.warn(`[VU${__VU}] 자소서 폴링 타임아웃 (${POLL_MAX_WAIT_S}s 초과)`);
    }
  }

  // ── Step 4: Cleanup ─────────────────────────────────────────────────────
  http.del(ENDPOINTS.application(appId), null, {
    headers: headers,
    tags: { type: 'write', name: 'delete_application' },
  });

  sleep(0.5);
}
