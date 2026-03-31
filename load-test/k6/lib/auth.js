import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './endpoints.js';

/**
 * 테스트용 JWT 토큰.
 *
 * 부하 테스트는 OAuth2 흐름을 탈 수 없으므로 아래 방식 중 하나를 사용한다.
 *
 * 방법 A (권장): 미리 발급한 장기 토큰을 환경변수로 주입
 *   k6 run -e TEST_JWT_TOKEN=<token> scenarios/ramp-up.js
 *
 * 방법 B: /internal/load-test/token 엔드포인트로 테스트 전용 토큰 발급
 *   → Spring Boot에 해당 엔드포인트 추가 필요 (TODO: 구현 후 주석 해제)
 */
export function getAuthHeaders() {
  const token = __ENV.TEST_JWT_TOKEN || '';
  if (!token) {
    // 토큰 없이도 인증 불필요 엔드포인트 테스트는 가능
    return {};
  }
  return {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

/**
 * 스텁 모드 전환 헤더
 * LOAD_TEST_KEY 환경변수에서 읽음
 */
export function getLoadTestKeyHeaders() {
  return {
    'X-Load-Test-Key': __ENV.LOAD_TEST_KEY || '',
    'Content-Type': 'application/json',
  };
}

/**
 * 테스트 시작 전 스텁 모드 활성화.
 * FakeAiClient 구현 완료 후 setup()에서 호출.
 */
export function enableStubMode(loadTestStatusUrl, loadTestEnableUrl) {
  const res = http.post(loadTestEnableUrl, null, {
    headers: getLoadTestKeyHeaders(),
  });
  check(res, {
    'stub mode enabled': (r) => r.status === 200,
  });
}

/**
 * 테스트 종료 후 스텁 모드 비활성화.
 * teardown()에서 호출.
 */
export function disableStubMode(loadTestDisableUrl) {
  http.post(loadTestDisableUrl, null, {
    headers: getLoadTestKeyHeaders(),
  });
}
