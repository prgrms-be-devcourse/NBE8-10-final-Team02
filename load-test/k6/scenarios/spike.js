/**
 * spike.js
 *
 * 순간 폭발 부하 시나리오.
 * 0 → MAX_VUS 를 10초 만에 올렸다가 바로 내림.
 * 스케일 한계, 에러율 폭발 지점 확인에 사용.
 *
 * 실행:
 *   VUS=100 BASE_URL=http://<IP>:8080 k6 run load-test/k6/scenarios/spike.js
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { getAuthHeaders } from '../lib/auth.js';
import { assertResponse, assertHealthy } from '../lib/checks.js';

const MAX_VUS = parseInt(__ENV.VUS || '50');

export const options = {
  stages: [
    { duration: '10s', target: 0        },  // 대기
    { duration: '10s', target: MAX_VUS  },  // 급격한 증가 (스파이크)
    { duration: '30s', target: MAX_VUS  },  // 최고 부하 유지
    { duration: '10s', target: 0        },  // 급격한 감소
    { duration: '15s', target: 0        },  // 회복 확인
  ],
  thresholds: {
    // 스파이크는 느슨하게: p95 3초, 에러율 10%
    'http_req_duration': ['p(95)<3000'],
    'api_error_rate':    ['rate<0.10'],
  },
};

export default function () {
  const authHeaders = getAuthHeaders();

  const res = http.get(ENDPOINTS.health, { tags: { type: 'health' } });
  assertHealthy(res);

  sleep(0.1);

  const csRes = http.get(ENDPOINTS.csQuestions, {
    headers: authHeaders,
    tags: { type: 'read' },
  });
  assertResponse(csRes, [200], 3000);

  sleep(0.5);
}
