'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';

// --- API & Types Imports ---
import { getPortfolioReadiness, UnauthenticatedError } from '@/api/portfolio';
import { useAiStatus } from '@/hooks/useAiStatus';
import type {
  PortfolioConnectionStatus,
  PortfolioMissingItem,
  PortfolioNextRecommendedAction,
  PortfolioReadinessDashboard,
} from '@/types/portfolio';
import type { AiProviderStatus } from '@/types/aistatus';

// ==========================================
// 1. Constants & Helpers
// ==========================================
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

const aiStatusColors: Record<AiProviderStatus, string> = {
  available: 'bg-emerald-500',
  minute_rate_limited: 'bg-amber-500',
  daily_exhausted: 'bg-red-500',
};

const aiStatusLabels: Record<AiProviderStatus, string> = {
  available: '정상',
  minute_rate_limited: '일시 제한',
  daily_exhausted: '일일 한도 초과',
};

const progressTrackColor: Record<AiProviderStatus, string> = {
  available: 'bg-emerald-500',
  minute_rate_limited: 'bg-amber-500',
  daily_exhausted: 'bg-red-500',
};

function formatMissingItems(items: PortfolioMissingItem[]) {
  if (items.length === 0) return '필수 부족 항목 없음';
  if (items.length === 1) return `${missingItemLabel[items[0]]} 필요`;
  return `${missingItemLabel[items[0]]} 외 ${items.length - 1}개`;
}

/** 사용률 progress bar */
function UsageBar({ percentage, status }: { percentage: number; status: AiProviderStatus }) {
  const clamped = Math.min(100, Math.max(0, percentage));
  return (
    <div className="mt-1 h-1 w-full rounded-full bg-zinc-200">
      <div
        className={`h-1 rounded-full transition-all ${progressTrackColor[status]}`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}

// ==========================================
// 2. Main Widget Component
// ==========================================
export default function PortfolioReadinessWidget() {
  const pathname = usePathname();

  // Portfolio States
  const [dashboard, setDashboard] = useState<PortfolioReadinessDashboard | null>(null);
  const [loadingPortfolio, setLoadingPortfolio] = useState(true);
  const [errorPortfolio, setErrorPortfolio] = useState<string | null>(null);
  const [unauthenticated, setUnauthenticated] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  // AI Status (폴링 + 카운트다운)
  const { aiStatus, loading: loadingAi, error: errorAi, countdown } = useAiStatus();

  // --- Portfolio Data Fetching ---
  useEffect(() => {
    async function loadPortfolio() {
      setLoadingPortfolio(true);
      setErrorPortfolio(null);
      try {
        const data = await getPortfolioReadiness();
        setDashboard(data);
      } catch (err) {
        if (err instanceof UnauthenticatedError) {
          setUnauthenticated(true);
        } else {
          setErrorPortfolio(err instanceof Error ? err.message : '준비 현황을 불러오지 못했습니다.');
        }
      } finally {
        setLoadingPortfolio(false);
      }
    }
    void loadPortfolio();
  }, [pathname, reloadToken]);

  // --- Render: Loading & Auth States ---
  if (loadingPortfolio) {
    return (
      <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
        <p className="text-xs font-medium text-zinc-500">준비 현황</p>
        <p className="mt-3 text-sm text-zinc-400">불러오는 중...</p>
      </div>
    );
  }

  if (unauthenticated) {
    return (
      <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
        <p className="mt-2 text-sm text-zinc-600">서비스를 이용하려면 로그인이 필요합니다.</p>
        <a href="/login" className="mt-4 inline-flex w-full items-center justify-center rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white">
          로그인하기
        </a>
      </div>
    );
  }

  if (errorPortfolio || !dashboard) {
    return (
      <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs font-medium text-zinc-500">준비 현황</p>
            <p className="mt-1 text-sm font-semibold text-zinc-900">요약을 불러오지 못했습니다.</p>
          </div>
          <button type="button" onClick={() => setReloadToken((v) => v + 1)} className="text-xs font-medium text-zinc-500 underline">
            다시 조회
          </button>
        </div>
        <p className="mt-3 text-sm text-zinc-500">{errorPortfolio ?? '잠시 후 다시 확인해주세요.'}</p>
      </div>
    );
  }

  const nextAction = nextActionConfig[dashboard.readiness.nextRecommendedAction];

  // --- Render: Main Widget ---
  return (
    <div className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
      {/* 1. 포트폴리오 현황 섹션 */}
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium text-zinc-500">준비 현황</p>
          <h2 className="mt-1 text-lg font-semibold text-zinc-900">{dashboard.profile.displayName}님의 상태</h2>
        </div>
        <div className="flex items-center gap-3">
          <button type="button" onClick={() => setReloadToken((v) => v + 1)} className="text-xs font-medium text-zinc-500 underline">
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
          <dd className="mt-1 font-medium text-zinc-900">{connectionStatusLabel[dashboard.github.connectionStatus]}</dd>
        </div>
        <div className="rounded-xl bg-zinc-50 px-3 py-2">
          <dt className="text-zinc-500">repository</dt>
          <dd className="mt-1 font-medium text-zinc-900">{dashboard.github.selectedRepositoryCount}개</dd>
        </div>
        <div className="rounded-xl bg-zinc-50 px-3 py-2">
          <dt className="text-zinc-500">문서 성공</dt>
          <dd className="mt-1 font-medium text-zinc-900">{dashboard.documents.extractSuccessCount}개</dd>
        </div>
        <div className="rounded-xl bg-zinc-50 px-3 py-2">
          <dt className="text-zinc-500">지원 준비</dt>
          <dd className="mt-1 font-medium text-zinc-900">{dashboard.readiness.canStartApplication ? '가능' : '보완 필요'}</dd>
        </div>
      </dl>

      {/* 최근 실패 작업 요약 — ready 상태이고 실패 항목이 있을 때만 표시 */}
      {dashboard.alerts.recentFailedJobs.status === 'ready' &&
        !!dashboard.alerts.recentFailedJobs.items?.length && (
          <div className="mt-3 rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-sm">
            <p className="font-medium text-red-800">
              최근 실패 {dashboard.alerts.recentFailedJobs.items.length}건
            </p>
            <p className="mt-0.5 text-xs text-red-500 truncate">
              {dashboard.alerts.recentFailedJobs.items[0].message}
            </p>
          </div>
        )}

      <div className="mt-4 rounded-xl border border-zinc-200 px-3 py-3">
        <p className="text-xs font-medium text-zinc-500">부족한 항목</p>
        <p className="mt-1 text-sm text-zinc-700">{formatMissingItems(dashboard.readiness.missingItems)}</p>
      </div>

      {/* 구분선 */}
      <hr className="my-5 border-zinc-100" />

      {/* 2. AI 서비스 상태 섹션 */}
      <div>
        <div className="flex items-center gap-2">
          <p className="text-xs font-medium text-zinc-500">AI 엔진 상태</p>
          {!loadingAi && !errorAi && aiStatus && (
            <span className="relative flex h-2 w-2">
              {aiStatus.available && (
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
              )}
              <span className={`relative inline-flex h-2 w-2 rounded-full ${aiStatus.available ? 'bg-emerald-500' : 'bg-amber-500'}`} />
            </span>
          )}
        </div>

        {loadingAi && <p className="mt-1 text-sm text-zinc-400">상태 확인 중...</p>}
        {errorAi && <p className="mt-1 text-sm text-red-500">{errorAi}</p>}

        {!loadingAi && !errorAi && aiStatus && (
          <div className="mt-2 flex flex-col gap-2">
            {/* 비가용 알림 배너 */}
            {!aiStatus.available && (
              <div className={`rounded-lg px-3 py-2 text-xs ${
                aiStatus.providers.every((p) => p.status === 'daily_exhausted')
                  ? 'bg-red-50 text-red-800'
                  : 'bg-amber-50 text-amber-800'
              }`}>
                <p className="font-medium">{aiStatus.message}</p>
                {countdown !== null && (
                  <p className="mt-0.5 text-amber-700">
                    약 <strong className="font-bold">{countdown}초</strong> 후 자동 재확인
                  </p>
                )}
              </div>
            )}

            {/* Provider별 상태 카드 */}
            {aiStatus.providers.map((provider) => (
              <div key={provider.name} className="rounded-lg border border-zinc-100 bg-zinc-50 px-3 py-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-zinc-700">{provider.name}</span>
                  <span className="flex items-center gap-1.5 text-xs font-medium text-zinc-900">
                    <span className={`h-1.5 w-1.5 rounded-full ${aiStatusColors[provider.status]}`} />
                    {aiStatusLabels[provider.status]}
                  </span>
                </div>

                {/* 분당 요청 사용률 */}
                <div className="mt-2">
                  <div className="flex justify-between text-[10px] text-zinc-400">
                    <span>분당 요청</span>
                    <span>{provider.minuteUsage.used}/{provider.minuteUsage.limit} ({provider.minuteUsage.percentage}%)</span>
                  </div>
                  <UsageBar percentage={provider.minuteUsage.percentage} status={provider.status} />
                </div>

                {/* 일간 요청 사용률 */}
                <div className="mt-1.5">
                  <div className="flex justify-between text-[10px] text-zinc-400">
                    <span>일간 요청</span>
                    <span>{provider.dailyUsage.used}/{provider.dailyUsage.limit} ({provider.dailyUsage.percentage}%)</span>
                  </div>
                  <UsageBar percentage={provider.dailyUsage.percentage} status={provider.status} />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 3. 하단 액션 버튼 */}
      <Link
        href={nextAction.href}
        className={`mt-5 inline-flex w-full items-center justify-center rounded-full px-4 py-2.5 text-sm font-medium text-white transition-colors
          ${aiStatus && !aiStatus.available && nextAction.href === '/applications'
          ? 'pointer-events-none bg-zinc-400'
          : 'bg-zinc-900 hover:bg-zinc-800'}`}
      >
        {aiStatus && !aiStatus.available && nextAction.href === '/applications'
          ? 'AI 기능 대기 중...'
          : nextAction.label}
      </Link>
    </div>
  );
}
