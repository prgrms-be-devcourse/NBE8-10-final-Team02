import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './endpoints.js';

/**
 * 부하 테스트 시작 전 JWT 토큰을 확보한다.
 *
 * - TEST_JWT_TOKEN 환경변수가 있으면 그대로 사용.
 * - 없으면 /internal/load-test/token 엔드포인트에서 자동 발급 (load-test 프로파일 필요).
 *
 * 각 시나리오의 export function setup() 에서 호출하고,
 * 반환값을 default function(data) 의 data로 넘긴다.
 */
export function acquireToken() {
  const envToken = __ENV.TEST_JWT_TOKEN || '';
  if (envToken) {
    return envToken;
  }

  const res = http.get(`${BASE_URL}/internal/load-test/token`);
  const ok = check(res, {
    'token endpoint 200': (r) => r.status === 200,
  });
  if (!ok) {
    throw new Error(`토큰 발급 실패: ${res.status} ${res.body}`);
  }

  return res.json('data.token');
}

export function getAuthHeaders(token) {
  if (!token) {
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
