'use client';

import { useState } from 'react';
import { getAuthorizeUrl } from '@/api/auth';
import type { Provider } from '@/types/auth';

const PROVIDERS: { id: Provider; label: string }[] = [
  { id: 'github', label: 'GitHub로 로그인' },
  { id: 'google', label: 'Google로 로그인' },
  { id: 'kakao', label: 'Kakao로 로그인' },
];

export default function LoginPage() {
  const [loading, setLoading] = useState<Provider | null>(null);
  const [error, setError] = useState<string | null>(null);

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

        {error && (
          <p className="text-center text-sm text-red-600">
            {error}
            <button onClick={() => setError(null)} className="ml-2 underline">
              닫기
            </button>
          </p>
        )}

        <div className="flex flex-col gap-3">
          {PROVIDERS.map(({ id, label }) => (
            <button
              key={id}
              onClick={() => handleLogin(id)}
              disabled={loading !== null}
              className="h-12 rounded border border-zinc-300 px-4 font-medium disabled:opacity-50"
            >
              {loading === id ? '로딩 중...' : label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}