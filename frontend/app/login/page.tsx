'use client';

import { useState, useEffect, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { getAuthorizeUrl } from '@/api/auth';
import type { Provider } from '@/types/auth';

const PROVIDERS: { id: Provider; label: string }[] = [
  { id: 'github', label: 'GitHub로 로그인' },
  { id: 'google', label: 'Google로 로그인' },
  { id: 'kakao', label: 'Kakao로 로그인' },
];

function LoginContent() {
  const [loading, setLoading] = useState<Provider | null>(null);
  const [error, setError] = useState<string | null>(null);
  const searchParams = useSearchParams();

  useEffect(() => {
    const errorParam = searchParams.get('error');
    if (errorParam) {
      if (errorParam === 'cancelled') {
        setError('로그인이 취소되었습니다.');
      } else {
        setError(decodeURIComponent(errorParam));
      }
    }
  }, [searchParams]);

  async function handleLogin(provider: Provider) {
    setLoading(provider);
    setError(null);
    try {
      const url = await getAuthorizeUrl(provider);
      window.location.href = url;
    } catch {
      setError('로그인 중 오류가 발생했습니다. 다시 시도해주세요.');
      setLoading(null);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="flex w-full max-w-sm flex-col gap-4 p-8">
        <h1 className="text-center text-2xl font-semibold">AI 기술 면접 연습</h1>
        <p className="text-center text-sm text-zinc-500">소셜 계정으로 로그인하세요</p>
        <p className="text-center text-xs text-zinc-400">
          GitHub 계정으로 로그인하지 않아도 로그인 후 GitHub 연동을 지원합니다.
        </p>

        {error && (
          <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-600">
            <p className="inline">{error}</p>
            <button
              onClick={() => setError(null)}
              className="ml-2 font-semibold underline hover:text-red-800"
            >
              닫기
            </button>
          </div>
        )}

        <div className="flex flex-col gap-3">
          {PROVIDERS.map(({ id, label }) => (
            <button
              key={id}
              onClick={() => handleLogin(id)}
              disabled={loading !== null}
              className="h-12 rounded border border-zinc-300 px-4 font-medium transition-colors hover:bg-zinc-50 disabled:opacity-50"
            >
              {loading === id ? '로딩 중...' : label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="flex min-h-screen items-center justify-center">로딩 중...</div>}>
      <LoginContent />
    </Suspense>
  );
}
