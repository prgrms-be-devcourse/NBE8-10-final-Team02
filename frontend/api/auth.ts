import type { Provider, User } from '@/types/auth';
import { apiFetch } from '@/lib/api-client';

/**
 * 소셜 로그인 시작 URL 조회.
 * GET /api/v1/auth/oauth2/{provider}/authorize
 */
export async function getAuthorizeUrl(provider: Provider): Promise<string> {
  const res = await apiFetch(`/api/v1/auth/oauth2/${provider}/authorize`);
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
    const res = await apiFetch(`/api/v1/users/me`, {
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
 * 이미 로그인된 사용자가 GitHub 계정을 추가 연동하기 위한 OAuth2 인증 URL 조회.
 * GET /api/v1/auth/oauth2/github/link-url
 * 반환된 authorizationUrl로 브라우저를 이동시키면 GitHub OAuth 흐름이 시작된다.
 */
export async function getGithubLinkUrl(redirectUrl?: string): Promise<string> {
  const params = new URLSearchParams();
  if (redirectUrl) params.set('redirectUrl', redirectUrl);
  const res = await apiFetch(`/api/v1/auth/oauth2/github/link-url?${params}`, {
    cache: 'no-store',
  });
  if (!res.ok) throw new Error(`github link url fetch failed: ${res.status}`);
  const body = await res.json();
  return body.data.authorizationUrl as string;
}

/**
 * 로그아웃.
 * POST /api/v1/auth/logout
 */
export async function logout(): Promise<void> {
  await apiFetch(`/api/v1/auth/logout`, {
    method: 'POST',
  });
}
