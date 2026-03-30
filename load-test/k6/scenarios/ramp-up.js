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
import { getAuthHeaders } from '../lib/auth.js';
import { assertResponse, assertHealthy } from '../lib/checks.js';

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
  const authHeaders = getAuthHeaders();

  // 1. 헬스 체크
  {
    const res = http.get(ENDPOINTS.health, { tags: { type: 'health' } });
    assertHealthy(res);
  }

  sleep(0.5);

  // 2. CS 질문 목록 조회 (읽기, AI 없음)
  {
    const res = http.get(ENDPOINTS.csQuestions, {
      headers: authHeaders,
      tags: { type: 'read' },
    });
    assertResponse(res, [200], 1000);
  }

  sleep(0.3);

  // 3. 내 프로필 조회 (인증 필요)
  if (authHeaders['Authorization']) {
    const res = http.get(ENDPOINTS.me, {
      headers: authHeaders,
      tags: { type: 'read' },
    });
    assertResponse(res, [200], 1000);
    sleep(0.3);
  }

  // 4. Application 생성 (쓰기)
  // TODO: FakeAiClient 없이도 application 생성 자체는 AI 호출 없으므로 활성화 가능
  // if (authHeaders['Authorization']) {
  //   const payload = JSON.stringify({
  //     companyName: 'k6-test-company',
  //     jobTitle: '백엔드 개발자',
  //     jobDescription: '부하테스트용 임시 지원 공고',
  //   });
  //   const res = http.post(ENDPOINTS.applications, payload, {
  //     headers: authHeaders,
  //     tags: { type: 'write' },
  //   });
  //   assertResponse(res, [200, 201], 2000);
  //   sleep(0.5);
  // }

  // 5. 자소서 생성 (AI 호출) → FakeAiClient 준비 후 주석 해제
  // const genRes = http.post(
  //   ENDPOINTS.generateCoverLetter(appId),
  //   null,
  //   { headers: authHeaders, tags: { type: 'ai-gen' } }
  // );
  // assertResponse(genRes, [200, 202], 500); // fake는 즉시 응답

  sleep(1);
}
