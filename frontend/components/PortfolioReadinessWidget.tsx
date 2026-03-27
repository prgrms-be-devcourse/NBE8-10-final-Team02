'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { getPortfolioReadiness } from '@/api/portfolio';
import type {
  PortfolioConnectionStatus,
  PortfolioMissingItem,
  PortfolioNextRecommendedAction,
  PortfolioReadinessDashboard,
} from '@/types/portfolio';

const connectionStatusLabel: Record<PortfolioConnectionStatus, string> = {
  connected: '연결됨',
  not_connected: '미연결',
};

const missingItemLabel: Record<PortfolioMissingItem, string> = {
  github_connection: 'GitHub 연결',
  selected_repository: 'repository 선택',
  document_source: '문서 업로드',
  document_extract_success: '문서 추출 성공',
};

const nextActionConfig: Record<PortfolioNextRecommendedAction, { href: string; label: string }> = {
  connect_github: { href: '/portfolio/github', label: 'GitHub 연결' },
  select_repository: { href: '/portfolio/repositories', label: 'repository 선택' },
  upload_document: { href: '/portfolio/documents', label: '문서 업로드' },
  retry_document_extraction: { href: '/portfolio/documents', label: '문서 상태 확인' },
  start_application: { href: '/applications', label: '지원 준비 시작' },
};

function formatMissingItems(items: PortfolioMissingItem[]) {
  if (items.length === 0) {
    return '필수 부족 항목 없음';
  }

  if (items.length === 1) {
    return `${missingItemLabel[items[0]]} 필요`;
  }

  return `${missingItemLabel[items[0]]} 외 ${items.length - 1}개`;
}

export default function PortfolioReadinessWidget() {
  const pathname = usePathname();
  const [dashboard, setDashboard] = useState<PortfolioReadinessDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError(null);

      try {
        const data = await getPortfolioReadiness();
        setDashboard(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : '준비 현황을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    }

    void load();
  }, [pathname, reloadToken]);

  if (loading) {
    return (
      <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
        <p className="text-xs font-medium text-zinc-500">준비 현황</p>
        <p className="mt-3 text-sm text-zinc-400">불러오는 중...</p>
      </div>
    );
  }

  if (error || !dashboard) {
    return (
      <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs font-medium text-zinc-500">준비 현황</p>
            <p className="mt-1 text-sm font-semibold text-zinc-900">요약을 불러오지 못했습니다.</p>
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setReloadToken((value) => value + 1)}
              className="text-xs font-medium text-zinc-500 underline"
            >
              다시 조회
            </button>
            <Link href="/portfolio/readiness" className="text-xs font-medium text-zinc-700 underline">
              상세 보기
            </Link>
          </div>
        </div>
        <p className="mt-3 text-sm text-zinc-500">{error ?? '잠시 후 다시 확인해주세요.'}</p>
      </div>
    );
  }

  const nextAction = nextActionConfig[dashboard.readiness.nextRecommendedAction];

  return (
    <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium text-zinc-500">준비 현황</p>
          <h2 className="mt-1 text-lg font-semibold text-zinc-900">
            {dashboard.profile.displayName}님의 상태
          </h2>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setReloadToken((value) => value + 1)}
            className="text-xs font-medium text-zinc-500 underline"
          >
            새로고침
          </button>
          <Link href="/portfolio/readiness" className="text-xs font-medium text-zinc-700 underline">
            상세 보기
          </Link>
        </div>
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div className="rounded-xl bg-zinc-50 px-3 py-2">
          <dt className="text-zinc-500">GitHub</dt>
          <dd className="mt-1 font-medium text-zinc-900">
            {connectionStatusLabel[dashboard.github.connectionStatus]}
          </dd>
        </div>
        <div className="rounded-xl bg-zinc-50 px-3 py-2">
          <dt className="text-zinc-500">repository</dt>
          <dd className="mt-1 font-medium text-zinc-900">
            {dashboard.github.selectedRepositoryCount}개
          </dd>
        </div>
        <div className="rounded-xl bg-zinc-50 px-3 py-2">
          <dt className="text-zinc-500">문서 성공</dt>
          <dd className="mt-1 font-medium text-zinc-900">
            {dashboard.documents.extractSuccessCount}개
          </dd>
        </div>
        <div className="rounded-xl bg-zinc-50 px-3 py-2">
          <dt className="text-zinc-500">지원 준비</dt>
          <dd className="mt-1 font-medium text-zinc-900">
            {dashboard.readiness.canStartApplication ? '가능' : '보완 필요'}
          </dd>
        </div>
      </dl>

      <div className="mt-4 rounded-xl border border-zinc-200 px-3 py-3">
        <p className="text-xs font-medium text-zinc-500">부족한 항목</p>
        <p className="mt-1 text-sm text-zinc-700">
          {formatMissingItems(dashboard.readiness.missingItems)}
        </p>
      </div>

      <Link
        href={nextAction.href}
        className="mt-4 inline-flex w-full items-center justify-center rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white"
      >
        {nextAction.label}
      </Link>
    </div>
  );
}
