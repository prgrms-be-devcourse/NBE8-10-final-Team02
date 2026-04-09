/**
 * 전역 fetch 래퍼 (Interceptor 역할).
 * 401 Unauthorized 발생 시 로그아웃 처리 또는 재시도 로직을 통합 관리한다.
 */

const apiBase = () => process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';

export async function apiFetch(path: string, options: RequestInit = {}) {
  const url = path.startsWith('http') ? path : `${apiBase()}${path}`;

  const defaultOptions: RequestInit = {
    ...options,
    credentials: 'include', // 쿠키 포함 필수
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  };

  const response = await fetch(url, defaultOptions);

  if (response.status === 401) {
    // 백엔드 CookieJwtAuthenticationFilter가 Refresh Token으로 자동 갱신을 시도했음에도
    // 401이 반환되었다면 Refresh Token까지 만료된 상황임.

    if (typeof window !== 'undefined') {
      const currentPath = window.location.pathname;

      // 이미 메인 페이지(/)나 로그인 페이지(/login)에 있는 경우에는 무한 루프를 방지하기 위해 리다이렉트하지 않음.
      if (currentPath !== '/' && currentPath !== '/login') {
        console.warn('Session expired. Redirecting to login...');
        window.location.href = '/?expired=true';
      } else {
        console.log('Session expired but already on a safe page.');
      }
    }
  }

  return response;
}
