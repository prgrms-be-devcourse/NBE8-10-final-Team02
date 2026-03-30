/**
 * constant-vus.js
 *
 * 특정 VU 수를 일정 시간 유지하는 시나리오.
 * 병목 지점 확인, 안정성 측정에 사용.
 *
 * 실행:
 *   VUS=30 DURATION=3m BASE_URL=http://<IP>:8080 k6 run load-test/k6/scenarios/constant-vus.js
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { getAuthHeaders } from '../lib/auth.js';
import { assertResponse, assertHealthy } from '../lib/checks.js';

const VUS      = parseInt(__ENV.VUS || '10');
const DURATION = __ENV.DURATION || '2m';

export const options = {
  vus:      VUS,
  duration: DURATION,
  thresholds: {
    'http_req_duration{type:read}':   ['p(95)<1000'],
    'http_req_duration{type:write}':  ['p(95)<2000'],
    'http_req_duration{type:health}': ['p(95)<500'],
    'api_error_rate':                 ['rate<0.05'],
    'http_req_failed':                ['rate<0.05'],
  },
};

export default function () {
  const authHeaders = getAuthHeaders();

  const res = http.get(ENDPOINTS.health, { tags: { type: 'health' } });
  assertHealthy(res);
  sleep(0.2);

  const csRes = http.get(ENDPOINTS.csQuestions, {
    headers: authHeaders,
    tags: { type: 'read' },
  });
  assertResponse(csRes, [200], 1000);

  sleep(1);
}
