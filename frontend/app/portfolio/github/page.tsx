'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { connectGithub, refreshGithubConnection, getGithubConnection } from '@/api/github';
import { getMe, getGithubLinkUrl } from '@/api/auth';
import type { GithubConnection } from '@/types/github';
import type { Provider } from '@/types/auth';

// GitHub login 형식 검증 (백엔드와 동일한 규칙으로 프론트에서 선검증)
function isValidGithubLogin(login: string): boolean {
  return /^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$/.test(login);
}

type Mode = 'url' | 'oauth';

export default function GithubConnectPage() {
  const router = useRouter();

  // 초기 로딩: 현재 연결 상태 + 로그인 provider 확인
  const [loading, setLoading] = useState(true);
  const [existingConnection, setExistingConnection] = useState<GithubConnection | null>(null);
  const [providers, setProviders] = useState<Provider[]>([]);

  const [mode, setMode] = useState<Mode>('url');
  const [login, setLogin] = useState('');
  const [loginError, setLoginError] = useState<string | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const [connected, setConnected] = useState<GithubConnection | null>(null);

  // ── 진입 시: 이미 연결된 GitHub 계정 확인 ─────────────────
  // Google/Kakao 로그인 사용자도 이전에 연동한 적 있으면 여기서 감지된다.
  useEffect(() => {
    Promise.all([getMe(), getGithubConnection()])
      .then(([user, connection]) => {
        if (user) setProviders(user.connectedProviders ?? []);
        setExistingConnection(connection);
      })
      .catch(() => {
        // 오류 시 빈 상태로 폼 표시
      })
      .finally(() => setLoading(false));
  }, []);

  // ── URL 모드 제출 ────────────────────────────────────────
  async function handleUrlSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoginError(null);
    setApiError(null);

    if (!login.trim()) {
      setLoginError('GitHub 사용자 이름을 입력해주세요.');
      return;
    }
    if (!isValidGithubLogin(login.trim())) {
      setLoginError('올바르지 않은 GitHub 사용자 이름 형식입니다. 영문자, 숫자, 하이픈만 사용할 수 있습니다.');
      return;
    }

    setSubmitting(true);
    try {
      const result = await connectGithub({ mode: 'url', githubLogin: login.trim() });
      setConnected(result);
    } catch (err) {
      const msg = err instanceof Error ? err.message : '연결 중 오류가 발생했습니다.';
      if (msg.includes('요청 한도')) {
        setApiError(msg + '\n\nGitHub OAuth로 연동하면 시간당 5,000회로 한도가 늘어납니다.');
      } else {
        setApiError(msg);
      }
    } finally {
      setSubmitting(false);
    }
  }

  // ── GitHub OAuth 연동 (Google/Kakao 사용자) ──────────────────
  async function handleGithubOAuthLink() {
    setSubmitting(true);
    setApiError(null);
    try {
      const authorizationUrl = await getGithubLinkUrl('/portfolio/github');
      window.location.href = authorizationUrl;
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'GitHub OAuth 연동 중 오류가 발생했습니다.';
      setApiError(msg);
      setSubmitting(false);
    }
  }

  // ── GitHub OAuth 로그인 사용자: 저장된 token으로 repo 가져오기 ──
  async function handleRefreshFromOAuth() {
    setSubmitting(true);
    setApiError(null);
    try {
      const result = await refreshGithubConnection();
      setConnected(result);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'repository 목록을 가져오는 중 오류가 발생했습니다.';
      setApiError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  function handleGoToRepositories() {
    router.push('/portfolio/repositories');
  }

  // ── 로딩 중 ────────────────────────────────────────────
  if (loading) {
    return (
      <main className="mx-auto max-w-lg px-4 py-12">
        <p className="text-sm text-zinc-400">로딩 중...</p>
      </main>
    );
  }

  // ── 방금 연결 완료된 경우 ───────────────────────────────
  if (connected) {
    return (
      <main className="mx-auto max-w-lg px-4 py-12">
        <div className="rounded border border-green-200 bg-green-50 p-6">
          <h2 className="mb-1 text-lg font-semibold text-green-800">GitHub 연결 완료</h2>
          <p className="mb-4 text-sm text-green-700">
            <span className="font-medium">{connected.githubLogin}</span> 계정이 연결되었습니다.
          </p>
          <p className="mb-6 text-sm text-zinc-600">
            다음 단계에서 사용할 repository를 선택하고 커밋을 수집하세요.
          </p>
          <button
            onClick={handleGoToRepositories}
            className="w-full rounded bg-zinc-900 py-2.5 text-sm font-medium text-white"
          >
            repository 선택하기 →
          </button>
        </div>
      </main>
    );
  }

  // ── 이미 연결된 계정이 있는 경우 (Google/Kakao 포함) ─────
  // provider 상관없이 기존 github_connections 레코드가 있으면 표시
  if (existingConnection) {
    return (
      <main className="mx-auto max-w-lg px-4 py-12">
        <h1 className="mb-2 text-2xl font-semibold">GitHub 연결</h1>

        <div className="rounded border border-green-100 bg-green-50 px-4 py-4 mb-6">
          <p className="text-sm font-medium text-green-800 mb-1">이미 연결된 GitHub 계정이 있습니다</p>
          <p className="text-sm text-green-700">
            <span className="font-medium">{existingConnection.githubLogin}</span> 계정이 연결되어 있습니다.
          </p>
        </div>

        <div className="flex flex-col gap-3">
          <button
            onClick={handleGoToRepositories}
            className="w-full rounded bg-zinc-900 py-2.5 text-sm font-medium text-white"
          >
            repository 선택하기 →
          </button>
          <button
            onClick={() => setExistingConnection(null)}
            className="text-sm text-zinc-500 underline"
          >
            다른 GitHub 계정으로 변경하기
          </button>
        </div>
      </main>
    );
  }

  // ── GitHub OAuth로 로그인한 사용자 (연결 없음) ────────────
  // 로그인 시점에 token이 저장되므로 별도 입력 없이 repo를 가져올 수 있다.
  if (providers.includes('github')) {
    return (
      <main className="mx-auto max-w-lg px-4 py-12">
        <h1 className="mb-2 text-2xl font-semibold">GitHub 연결</h1>
        <p className="mb-8 text-sm text-zinc-500">
          GitHub 활동 내역을 바탕으로 자소서 생성과 면접 준비에 활용합니다.
        </p>

        <div className="rounded border border-blue-100 bg-blue-50 px-4 py-4 mb-6">
          <p className="text-sm font-medium text-blue-800 mb-1">GitHub 계정으로 로그인하셨습니다</p>
          <p className="text-xs text-blue-700">
            GitHub OAuth 토큰이 저장되어 있습니다. 아래 버튼을 눌러 repository 목록을 가져오세요.
          </p>
        </div>

        {apiError && (
          <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 whitespace-pre-line">
            {apiError}
            <button type="button" onClick={() => setApiError(null)} className="ml-2 underline text-red-500">닫기</button>
          </div>
        )}

        <button
          onClick={handleRefreshFromOAuth}
          disabled={submitting}
          className="w-full rounded bg-zinc-900 py-2.5 text-sm font-medium text-white disabled:opacity-50"
        >
          {submitting ? 'repository 가져오는 중...' : 'repository 목록 가져오기'}
        </button>
        <p className="mt-3 text-xs text-zinc-400">
          • private repository를 포함한 모든 repository가 조회됩니다.
        </p>
      </main>
    );
  }

  // ── Google / Kakao 로그인 사용자 (연결 없음) ──────────────
  return (
    <main className="mx-auto max-w-lg px-4 py-12">
      <h1 className="mb-2 text-2xl font-semibold">GitHub 연결</h1>
      <p className="mb-8 text-sm text-zinc-500">
        GitHub 활동 내역을 바탕으로 자소서 생성과 면접 준비에 활용합니다.
      </p>

      {/* 모드 탭 */}
      <div className="mb-6 flex rounded border border-zinc-200">
        <button
          onClick={() => { setMode('url'); setApiError(null); }}
          className={`flex-1 py-2 text-sm font-medium ${
            mode === 'url' ? 'bg-zinc-900 text-white' : 'bg-white text-zinc-600 hover:bg-zinc-50'
          }`}
        >
          URL 입력
        </button>
        <button
          onClick={() => { setMode('oauth'); setApiError(null); }}
          className={`flex-1 py-2 text-sm font-medium ${
            mode === 'oauth' ? 'bg-zinc-900 text-white' : 'bg-white text-zinc-600 hover:bg-zinc-50'
          }`}
        >
          GitHub OAuth 연동
        </button>
      </div>

      {/* URL 모드 */}
      {mode === 'url' && (
        <form onSubmit={handleUrlSubmit} className="flex flex-col gap-4">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-zinc-700">
              GitHub 사용자 이름
            </label>
            <input
              type="text"
              value={login}
              onChange={(e) => { setLogin(e.target.value); setLoginError(null); }}
              placeholder="예: octocat"
              disabled={submitting}
              className="w-full rounded border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-50"
            />
            {loginError && <p className="mt-1 text-xs text-red-600">{loginError}</p>}
          </div>

          <p className="text-xs text-zinc-400">
            • public repository만 조회됩니다.
            <br />
            • private repository가 필요하면 GitHub OAuth 연동 탭을 이용하세요.
          </p>

          {apiError && (
            <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 whitespace-pre-line">
              {apiError}
              <button type="button" onClick={() => setApiError(null)} className="ml-2 underline text-red-500">닫기</button>
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="rounded bg-zinc-900 py-2.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {submitting ? '연결 중...' : 'public repository 조회'}
          </button>
        </form>
      )}

      {/* OAuth 모드 */}
      {mode === 'oauth' && (
        <div className="flex flex-col gap-4">
          <div className="rounded border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-800">
            <p className="font-medium mb-1">private repository 접근 포함</p>
            <p className="text-xs text-blue-700">
              GitHub OAuth로 연동하면 private repository도 조회할 수 있고,
              API 요청 한도도 시간당 5,000회로 늘어납니다.
            </p>
          </div>

          {apiError && (
            <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 whitespace-pre-line">
              {apiError}
              <button type="button" onClick={() => setApiError(null)} className="ml-2 underline text-red-500">닫기</button>
            </div>
          )}

          <button
            onClick={handleGithubOAuthLink}
            disabled={submitting}
            className="rounded bg-zinc-900 py-2.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {submitting ? 'GitHub로 이동 중...' : 'GitHub OAuth 연동하기'}
          </button>
          <p className="text-xs text-zinc-400">
            • GitHub 로그인 페이지로 이동합니다. 완료 후 이 페이지로 돌아옵니다.
          </p>
        </div>
      )}
    </main>
  );
}
