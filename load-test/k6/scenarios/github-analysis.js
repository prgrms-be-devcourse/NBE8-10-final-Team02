/**
 * github-analysis.js — GitHub 레포 분석 동시 부하 시나리오
 *
 * 목적: 여러 사용자가 동시에 레포 분석을 트리거할 때 서버 안정성 확인.
 *       경량/중량/대형 레포가 섞인 상태에서 분석 파이프라인 처리량 측정.
 *
 * 전제: load-test 프로파일로 기동된 서버 (V100 시드 적용 상태)
 *       시드 레포 ID: 999001 (express ~5MB), 999002 (flask ~30MB), 999003 (spring-boot ~150MB)
 *
 * 실행:
 *   VUS=10 BASE_URL=http://43.201.36.166:8080 TEST_JWT_TOKEN=<token> TEST_API_KEY=<apiKey> ./run.sh github-analysis
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { acquireToken, getAuthHeaders } from '../lib/auth.js';
import { assertResponse } from '../lib/checks.js';

const MAX_VUS       = parseInt(__ENV.VUS || '5');
const POLL_INTERVAL = 5;    // 폴링 간격 (초)
const MAX_WAIT_SEC  = 300;  // 분석 완료 최대 대기 시간 (5분)

// V100 시드에서 고정된 레포 ID (github_repositories.id)
const REPOS = [
  { id: 999001, name: 'expressjs/express',          size: 'light'  },
  { id: 999002, name: 'pallets/flask',               size: 'medium' },
  { id: 999003, name: 'spring-projects/spring-boot', size: 'heavy'  },
];

export const options = {
  stages: [
    { duration: '10s', target: MAX_VUS },  // 동시 분석 트리거
    { duration: '5m',  target: MAX_VUS },  // 분석 완료 대기 (대형 레포 포함)
    { duration: '10s', target: 0       },
  ],
  thresholds: {
    'http_req_duration{type:analyze-trigger}': ['p(95)<3000'],
    'http_req_duration{type:status-poll}':     ['p(95)<1000'],
    'http_req_failed':                         ['rate<0.10'],
  },
  http: { timeout: '30s' },
};

export function setup() {
  return acquireToken(); // { token, apiKey }
}

export default function ({ token, apiKey }) {
  const headers = getAuthHeaders({ token, apiKey });

  // VU별로 레포 분산 (경량/중량/대형 골고루)
  const repo = REPOS[(__VU - 1) % REPOS.length];

  // 분석 트리거 (202 즉시 반환 — 비동기)
  const triggerRes = http.post(
    ENDPOINTS.analyzeRepository(repo.id),
    null,
    { headers: headers, tags: { type: 'analyze-trigger', size: repo.size } }
  );
  const triggered = assertResponse(triggerRes, [202], 3000);
  if (!triggered) {
    sleep(5);
    return;
  }

  console.log(`[VU${__VU}] ${repo.name} (${repo.size}) 분석 시작`);

  // 상태 폴링 — COMPLETED 또는 FAILED까지 대기
  let waited = 0;
  while (waited < MAX_WAIT_SEC) {
    sleep(POLL_INTERVAL);
    waited += POLL_INTERVAL;

    const statusRes = http.get(
      ENDPOINTS.repoSyncStatus(repo.id),
      { headers: headers, tags: { type: 'status-poll', size: repo.size } }
    );
    if (statusRes.status !== 200) break;

    const status = statusRes.json('data.status');
    check(statusRes, { [`${repo.name} status=${status}`]: () => true });

    if (status === 'COMPLETED' || status === 'FAILED') {
      console.log(`[VU${__VU}] ${repo.name} 분석 종료: ${status} (${waited}s)`);
      break;
    }
  }
}
