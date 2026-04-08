/**
 * document-upload.js — 문서 업로드 부하 시나리오
 *
 * 목적: 다양한 크기의 파일(소/중/대)을 여러 사용자가 동시에 업로드할 때
 *       서버 I/O, 메모리, 파일 처리 한계 측정.
 *
 * 제약:
 *   - 사용자당 최대 5개 → 업로드 후 즉시 삭제로 제한 회피
 *   - 허용 확장자: PDF, DOCX, MD, TXT
 *
 * 실행:
 *   VUS=20 BASE_URL=http://13.125.255.89:8080 TEST_JWT_TOKEN=<token> TEST_API_KEY=<apiKey> ./run.sh document-upload
 */
import http from 'k6/http';
import { sleep } from 'k6';
import { ENDPOINTS } from '../lib/endpoints.js';
import { acquireToken, getAuthHeaders } from '../lib/auth.js';
import { assertResponse } from '../lib/checks.js';

const MAX_VUS = parseInt(__ENV.VUS || '10');

// 크기별 테스트 파일 정의 (텍스트로 크기 시뮬레이션)
const FILE_SPECS = [
  { label: 'small',  bytes: 10   * 1024, timeout: 5000  },  // 10KB
  { label: 'medium', bytes: 500  * 1024, timeout: 10000 },  // 500KB
  { label: 'large',  bytes: 2048 * 1024, timeout: 30000 },  // 2MB
];

function generateContent(size) {
  const line = '자소서 부하테스트 문서 내용입니다. This is a load test document content for stress testing. ';
  let content = '';
  while (content.length < size) content += line;
  return content.slice(0, size);
}

// 한 번만 생성해서 재사용 (VU별로 공유)
const FILES = FILE_SPECS.map(spec => ({
  ...spec,
  content: generateContent(spec.bytes),
}));

export const options = {
  stages: [
    { duration: '10s', target: Math.min(5, MAX_VUS) },
    { duration: '60s', target: MAX_VUS               },
    { duration: '10s', target: 0                     },
  ],
  thresholds: {
    'http_req_duration{type:upload-small}':  ['p(95)<5000'],
    'http_req_duration{type:upload-medium}': ['p(95)<10000'],
    'http_req_duration{type:upload-large}':  ['p(95)<30000'],
    'api_error_rate':  ['rate<0.05'],
    'http_req_failed': ['rate<0.05'],
  },
  // url을 systemTags에서 제외 → 동적 ID가 Prometheus 레이블로 올라가지 않아 high cardinality 방지
  systemTags: ['status', 'method', 'name', 'check', 'error', 'error_code', 'scenario'],
};

export function setup() {
  return acquireToken(); // { token, apiKey }
}

export default function ({ token, apiKey }) {
  const headers = getAuthHeaders({ token, apiKey });
  const spec    = FILES[__ITER % FILES.length]; // 소→중→대 순환

  const uploadRes = http.post(
    ENDPOINTS.documents,
    {
      file:         http.file(spec.content, `load-test-${spec.label}-${__VU}-${Date.now()}.txt`, 'text/plain'),
      documentType: 'other',
    },
    {
      headers: { Authorization: headers['Authorization'] },
      tags:    { type: `upload-${spec.label}` },
    }
  );

  if (assertResponse(uploadRes, [201], spec.timeout)) {
    const docId = uploadRes.json('data.id');
    if (docId) {
      // 사용자당 5개 제한 초과 방지를 위해 즉시 삭제
      http.del(
        ENDPOINTS.document(docId),
        null,
        { headers: headers, tags: { type: 'delete', name: 'delete_document' } }
      );
    }
  }

  sleep(1);
}
