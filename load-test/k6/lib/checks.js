import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// ── 커스텀 메트릭 ────────────────────────────────────────────────────────
export const apiDuration    = new Trend('api_duration', true);
export const apiErrorRate   = new Rate('api_error_rate');
export const apiCallCount   = new Counter('api_call_count');

/**
 * 공통 응답 검증.
 * @param {object} res   - k6 http response
 * @param {number[]} okStatuses - 성공으로 볼 HTTP 상태코드 목록
 * @param {number} maxDurationMs - p95 목표 응답시간 (ms)
 */
export function assertResponse(res, okStatuses = [200, 201], maxDurationMs = 2000) {
  const passed = check(res, {
    [`status in [${okStatuses.join(',')}]`]:
      (r) => okStatuses.includes(r.status),
    [`response time < ${maxDurationMs}ms`]:
      (r) => r.timings.duration < maxDurationMs,
    'has requestId header':
      (r) => !!r.headers['X-Request-Id'],
    'response is JSON':
      (r) => r.headers['Content-Type'] && r.headers['Content-Type'].includes('application/json'),
  });

  apiDuration.add(res.timings.duration);
  apiErrorRate.add(!passed);
  apiCallCount.add(1);

  return passed;
}

/**
 * 단순 헬스체크 검증 (requestId 헤더 없어도 됨)
 */
export function assertHealthy(res) {
  return check(res, {
    'health status 200': (r) => r.status === 200,
    'health response time < 500ms': (r) => r.timings.duration < 500,
  });
}

/**
 * 에러 응답 구조 검증 (4xx, 5xx)
 */
export function assertErrorShape(res, expectedStatus) {
  return check(res, {
    [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
    'error response has code field': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.error && body.error.code;
      } catch {
        return false;
      }
    },
  });
}
