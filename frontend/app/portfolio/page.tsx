'use client';

import Link from 'next/link';
import { useState, useEffect } from 'react';
import { getGithubConnection } from '@/api/github';
import type { GithubConnection } from '@/types/github';

export default function PortfolioPage() {
  const [connection, setConnection] = useState<GithubConnection | null>(null);
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    getGithubConnection()
      .then(setConnection)
      .catch(() => setConnection(null))
      .finally(() => setChecked(true));
  }, []);

  return (
    <main className="mx-auto max-w-2xl px-4 py-16">
      <h1 className="mb-2 text-2xl font-semibold">포트폴리오</h1>
      <p className="mb-10 text-sm text-zinc-500">
        GitHub 활동을 연동하고 커밋을 수집해 AI 자소서 생성과 면접 준비에 활용하세요.
      </p>

      <ol className="flex flex-col gap-4">
        {/* Step 1 */}
        <li className="rounded border border-zinc-200 px-5 py-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs font-medium text-zinc-400 mb-0.5">Step 1</p>
              <p className="text-sm font-medium">GitHub 연결</p>
              <p className="mt-0.5 text-xs text-zinc-500">
                GitHub 계정을 연결해 활동 내역을 가져옵니다.
              </p>
              {checked && connection && (
                <p className="mt-1.5 text-xs font-medium text-green-600">
                  ✓ @{connection.githubLogin} 연결됨
                </p>
              )}
            </div>
            <div className="shrink-0 flex flex-col items-end gap-1.5">
              {!checked ? (
                <span className="text-xs text-zinc-400">확인 중...</span>
              ) : connection ? (
                <Link
                  href="/portfolio/github"
                  className="rounded border border-zinc-300 px-4 py-1.5 text-sm font-medium text-zinc-700 hover:bg-zinc-50"
                >
                  변경하기
                </Link>
              ) : (
                <Link
                  href="/portfolio/github"
                  className="rounded bg-zinc-900 px-4 py-1.5 text-sm font-medium text-white"
                >
                  연결하기
                </Link>
              )}
            </div>
          </div>
        </li>

        {/* Step 2 */}
        <li className="rounded border border-zinc-200 px-5 py-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs font-medium text-zinc-400 mb-0.5">Step 2</p>
              <p className="text-sm font-medium">Repository 선택</p>
              <p className="mt-0.5 text-xs text-zinc-500">
                커밋을 수집할 repository를 선택하고 동기화합니다.
              </p>
            </div>
            <Link
              href="/portfolio/repositories"
              className="shrink-0 rounded border border-zinc-300 px-4 py-1.5 text-sm font-medium text-zinc-700 hover:bg-zinc-50"
            >
              선택하기
            </Link>
          </div>
        </li>

        {/* Step 3 */}
        <li className="rounded border border-zinc-200 px-5 py-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs font-medium text-zinc-400 mb-0.5">Step 3</p>
              <p className="text-sm font-medium">문서 업로드</p>
              <p className="mt-0.5 text-xs text-zinc-500">
                이력서, 수상기록 등 문서를 업로드하고 텍스트를 추출합니다.
              </p>
            </div>
            <Link
              href="/portfolio/documents"
              className="shrink-0 rounded border border-zinc-300 px-4 py-1.5 text-sm font-medium text-zinc-700 hover:bg-zinc-50"
            >
              업로드하기
            </Link>
          </div>
        </li>
      </ol>
    </main>
  );
}
