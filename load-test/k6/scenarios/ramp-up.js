/**
 * ramp-up.js
 *
 * VU를 1에서 MAX_VUS까지 단계적으로 늘리는 메인 시나리오.
 *
 * 실행:
 *   VUS=50 BASE_URL=http://<IP>:8080 k6 run load-test/k6/scenarios/ramp-up.js
 *   VUS=100 BASE_URL=http://<IP>:8080 TEST_JWT_TOKEN=<token> k6 run load-test/k6/scenarios/ramp-up.js
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
// import { getAuthHeaders } from '../lib/auth.js';
import { assertHealthy } from '../lib/checks.js';
// import { assertResponse } from '../lib/checks.js';

const MAX_VUS = parseInt(__ENV.VUS || '10');

export const options = {
  stages: [
    { duration: '15s', target: 1          },  // 워밍업
    { duration: '30s', target: Math.min(10, MAX_VUS)  },
    { duration: '30s', target: Math.min(30, MAX_VUS)  },
    { duration: '30s', target: Math.min(50, MAX_VUS)  },
    { duration: '30s', target: MAX_VUS     },  // 최대 부하
    { duration: '30s', target: MAX_VUS     },  // 최대 부하 유지
    { duration: '20s', target: 0           },  // 쿨다운
  ],
  thresholds: {
    // docs/non-functional.md 기준
    'http_req_duration{type:read}':   ['p(95)<1000'],
    'http_req_duration{type:write}':  ['p(95)<2000'],
    'http_req_duration{type:health}': ['p(95)<500'],
    'api_error_rate':                 ['rate<0.05'],  // 에러율 5% 미만
    'http_req_failed':                ['rate<0.05'],
  },
};

// ── 시나리오 본문 ──────────────────────────────────────────────────────────
export default function () {
  // 환경 확인용: health 체크만 활성화
  const res = http.get(ENDPOINTS.health, { tags: { type: 'health' } });
  assertHealthy(res);
  sleep(1);

  // TODO: 환경 확인 후 아래 시나리오 순차 활성화
  // const authHeaders = getAuthHeaders();

  // 2. CS 질문 목록 조회
  // const csRes = http.get(ENDPOINTS.csQuestions, { headers: authHeaders, tags: { type: 'read' } });
  // assertResponse(csRes, [200], 1000);

  // 3. 내 프로필 조회 (JWT 필요)
  // if (authHeaders['Authorization']) {
  //   const meRes = http.get(ENDPOINTS.me, { headers: authHeaders, tags: { type: 'read' } });
  //   assertResponse(meRes, [200], 1000);
  // }

  // 4. Application 생성 (AI 없이 가능)
  // 5. 자소서/질문 생성 → FakeAiClient 구현 후 활성화
}
