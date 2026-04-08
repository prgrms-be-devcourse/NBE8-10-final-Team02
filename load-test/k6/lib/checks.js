import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// ── 커스텀 메트릭 ────────────────────────────────────────────────────────
export const apiDuration    = new Trend('api_duration', true);
export const apiErrorRate   = new Rate('api_error_rate');
export const apiCallCount   = new Counter('api_call_count');

/**
 * AI 생성 엔드포인트별 p95 목표 응답시간 (ms).
 * Stub 기준: 입력 토큰(코드 분석 묶음) + 출력 토큰 규모를 반영한 값.
 *
 * 산출 근거 (Gemini 2.5 Flash stub, jitter ×1.2 상한 적용):
 *   self-intro:  입력 ~2,571tok + 출력 ~1,200tok → 16,814ms × 1.2 ≈ 20,200ms
 *   interview-questions: 입력 ~286tok + 출력 ~600tok → 7,691ms × 1.2 ≈ 9,230ms
 *   evaluate/followup:   입력 ~200tok + 출력 ~200tok → 3,381ms × 1.2 ≈ 4,060ms
 */
export const AI_TIMEOUT = {
  selfIntro:           25_000,
  interviewQuestions:  12_000,
  evaluate:             6_000,
  followup:             6_000,
  summary:              8_000,
};

/**
 * 공통 응답 검증.
 *
 * api_error_rate는 "요청 자체의 성공 여부"만 반영한다.
 * latency 목표 초과는 별도 check로 기록하되 에러율에는 합산하지 않는다.
 * (200 OK지만 느린 응답을 에러로 오집계하는 문제 방지)
 *
 * @param {object}   res          - k6 http response
 * @param {number[]} okStatuses   - 성공으로 볼 HTTP 상태코드 목록
 * @param {number}   maxDurationMs - 응답 시간 목표 (ms) — 초과 시 check 실패로만 기록
 */
export function assertResponse(res, okStatuses = [200, 201], maxDurationMs = 2000) {
  // 성공 여부 판단: status + 헤더 구조만 검사
  const statusOk = check(res, {
    [`status in [${okStatuses.join(',')}]`]:
      (r) => okStatuses.includes(r.status),
    'has requestId header':
      (r) => !!r.headers['X-Request-Id'],
    'response is JSON':
      (r) => r.headers['Content-Type'] && r.headers['Content-Type'].includes('application/json'),
  });

  // latency 목표는 별도 check — api_error_rate에 영향 없음
  check(res, {
    [`response time < ${maxDurationMs}ms`]:
      (r) => r.timings.duration < maxDurationMs,
  });

  apiDuration.add(res.timings.duration);
  apiErrorRate.add(!statusOk);
  apiCallCount.add(1);

  return statusOk;
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
