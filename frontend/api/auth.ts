import type { Provider, User } from '@/types/auth';

/** 브라우저 → Next.js 프록시 → 백엔드 경로로 통일. 서버 컴포넌트에서 호출할 때는 절대 URL이 필요하므로 환경변수를 통해 설정한다. */
const apiBase = () => process.env.NEXT_PUBLIC_API_BASE_URL ?? '';

/**
 * 소셜 로그인 시작 URL 조회.
 * GET /api/v1/auth/oauth2/{provider}/authorize
 */
export async function getAuthorizeUrl(provider: Provider): Promise<string> {
  const res = await fetch(`${apiBase()}/api/v1/auth/oauth2/${provider}/authorize`);
  if (!res.ok) throw new Error(`authorize url fetch failed: ${res.status}`);
  const body = await res.json();
  return body.data.authorizationUrl as string;
}

/**
 * 현재 로그인 사용자 정보 조회.
 * GET /api/v1/users/me
 * 미인증이면 null 반환.
 */
export async function getMe(): Promise<User | null> {
  try {
    const res = await fetch(`${apiBase()}/api/v1/users/me`, {
      credentials: 'include',
      cache: 'no-store',
    });
    if (!res.ok) return null;
    const body = await res.json();
    return (body.data as User) ?? null;
  } catch {
    return null;
  }
}

/**
 * 로그아웃.
 * POST /api/v1/auth/logout
 */
export async function logout(): Promise<void> {
  await fetch(`${apiBase()}/api/v1/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  });
}