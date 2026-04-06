// api/ai.ts
import type { AiStatusResponse } from '@/types/aistatus';

const base = () =>
  (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000') + '/api/v1/ai';

// 공통 에러 파싱 (기존 코드 재사용)
async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body?.error?.message ?? `오류가 발생했습니다. (${res.status})`;
  } catch {
    return `오류가 발생했습니다. (${res.status})`;
  }
}

/**
 * GET /status
 * AI 서비스 가용성 상태 조회.
 * 각 provider의 사용량과 가용성 상태를 반환한다.
 */
export async function getAiStatus(): Promise<AiStatusResponse> {
  const res = await fetch(`${base()}/status`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
    // 인증 헤더나 쿠키가 필요하다면 아래 주석을 해제하세요.
    // credentials: 'include',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();

  // 주의: 만약 백엔드에서 공통 응답 래퍼(예: { data: {...}, status: 200 })를
  // 사용한다면 return body.data as AiStatusResponse; 로 변경해야 합니다.
  // 현재 컨트롤러 코드상으로는 객체 자체를 반환하므로 body를 바로 반환합니다.
  return body as AiStatusResponse;
}
