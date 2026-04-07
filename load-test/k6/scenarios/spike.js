/**
 * spike.js — AI Stub 순간 폭발 시나리오
 *
 * 목적: 동시 AI 요청이 급증할 때 커넥션 풀 포화 속도 및 에러율 임계점 측정.
 *       자소서 생성(~17s)이 몰릴 때 503/timeout 발생 지점 확인.
 *
 * 실행:
 *   VUS=50 BASE_URL=http://<IP>:8080 TEST_JWT_TOKEN=<token> TEST_API_KEY=<apiKey> ./run.sh spike
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { acquireToken, getAuthHeaders } from '../lib/auth.js';
import { assertResponse, AI_TIMEOUT } from '../lib/checks.js';

const MAX_VUS = parseInt(__ENV.VUS || '30');

export const options = {
  stages: [
    { duration: '10s', target: 0        },  // 대기 (서버 안정화)
    { duration: '10s', target: MAX_VUS  },  // 급격한 스파이크
    { duration: '60s', target: MAX_VUS  },  // 최고 부하 유지 (AI 응답 완료 대기)
    { duration: '10s', target: 0        },  // 급격한 감소
    { duration: '30s', target: 0        },  // 회복 확인 (남은 요청 처리 대기)
  ],
  thresholds: {
    // 스파이크는 느슨하게: 에러율 20% 미만만 체크 (응답시간 임계값 없음)
    'api_error_rate':   ['rate<0.20'],
    'http_req_failed':  ['rate<0.20'],
  },
  http: {
    timeout: '120s',
  },
};

export function setup() {
  return acquireToken(); // { token, apiKey }
}

export default function ({ token, apiKey }) {
  const headers = getAuthHeaders({ token, apiKey });
  if (!headers['Authorization']) {
    sleep(1);
    return;
  }

  // Application 생성
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
  if (!appId) {
    sleep(0.5);
    return;
  }

  // 문항 등록
  http.post(
    ENDPOINTS.applicationQuestions(appId),
    JSON.stringify({
      questions: [
        {
          questionOrder: 1,
          questionText: '자신을 소개해 주세요.',
          toneOption: 'formal',
          lengthOption: 'short',
          emphasisPoint: null,
        },
      ],
    }),
    { headers: headers, tags: { type: 'write' } }
  );

  // 자소서 AI 생성 — 이 호출이 동시에 몰리는 것이 스파이크의 핵심
  const aiRes = http.post(
    ENDPOINTS.generateAnswers(appId),
    JSON.stringify({ regenerate: false }),
    { headers: headers, tags: { type: 'ai-self-intro' }, timeout: '120s' }
  );
  assertResponse(aiRes, [200], AI_TIMEOUT.selfIntro);

  // Cleanup
  http.del(ENDPOINTS.application(appId), null, { headers: headers });

  sleep(0.5);
}
