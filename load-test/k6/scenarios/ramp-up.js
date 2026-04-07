/**
 * ramp-up.js — 비AI 혼합 베이스라인 시나리오
 *
 * 목적: AI 없는 읽기/쓰기 부하에서 응답 시간과 에러율 기준선을 측정.
 *       AI 시나리오 실행 전 서버 기본 성능 확인용.
 *
 * 실행:
 *   VUS=50 BASE_URL=http://<IP>:8080 TEST_JWT_TOKEN=<token> ./run.sh ramp-up
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { acquireToken, getAuthHeaders } from '../lib/auth.js';
import { assertHealthy, assertResponse } from '../lib/checks.js';

const MAX_VUS = parseInt(__ENV.VUS || '10');

export const options = {
  stages: [
    { duration: '15s', target: 1                        },  // 워밍업
    { duration: '30s', target: Math.min(10, MAX_VUS)   },
    { duration: '30s', target: Math.min(30, MAX_VUS)   },
    { duration: '30s', target: Math.min(50, MAX_VUS)   },
    { duration: '30s', target: MAX_VUS                  },  // 최대 부하
    { duration: '30s', target: MAX_VUS                  },  // 최대 부하 유지
    { duration: '20s', target: 0                        },  // 쿨다운
  ],
  thresholds: {
    'http_req_duration{type:health}': ['p(95)<500'],
    'http_req_duration{type:read}':   ['p(95)<1000'],
    'http_req_duration{type:write}':  ['p(95)<2000'],
    'api_error_rate':                 ['rate<0.05'],
    'http_req_failed':                ['rate<0.05'],
  },
};

export function setup() {
  return { token: acquireToken() };
}

export default function ({ token }) {
  const auth = getAuthHeaders(token);

  // 1. 헬스 체크
  const health = http.get(ENDPOINTS.health, { tags: { type: 'health' } });
  assertHealthy(health);

  // 2. CS 질문 목록 조회 (공개 엔드포인트)
  const cs = http.get(ENDPOINTS.csQuestions, {
    headers: auth,
    tags: { type: 'read' },
  });
  assertResponse(cs, [200], 1000);

  sleep(0.5);

  // 3. 내 프로필 조회 (JWT 필요 — 토큰 없으면 skip)
  if (auth['Authorization']) {
    const me = http.get(ENDPOINTS.me, {
      headers: auth,
      tags: { type: 'read' },
    });
    assertResponse(me, [200], 1000);

    // 4. Application 생성 → 조회 → 삭제 (쓰기 사이클)
    const createRes = http.post(
      ENDPOINTS.applications,
      JSON.stringify({
        applicationTitle: `load-test-${__VU}-${Date.now()}`,
        companyName: '테스트기업',
        jobRole: 'backend',
        applicationType: 'full_time',
      }),
      { headers: auth, tags: { type: 'write' } }
    );

    if (assertResponse(createRes, [200, 201], 2000)) {
      const appId = JSON.parse(createRes.body).data?.id;
      if (appId) {
        const getRes = http.get(ENDPOINTS.application(appId), {
          headers: auth,
          tags: { type: 'read' },
        });
        assertResponse(getRes, [200], 1000);

        http.del(ENDPOINTS.application(appId), null, {
          headers: auth,
          tags: { type: 'write' },
        });
      }
    }
  }

  sleep(1);
}
