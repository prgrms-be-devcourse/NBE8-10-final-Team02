'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState, useCallback } from 'react';

// --- API & Types Imports ---
import { getPortfolioReadiness, UnauthenticatedError } from '@/api/portfolio';
import { getAiStatus } from '@/api/aistatus';
import type {
  PortfolioConnectionStatus,
  PortfolioMissingItem,
  PortfolioNextRecommendedAction,
  PortfolioReadinessDashboard,
} from '@/types/portfolio';
import type { AiProviderStatus, AiStatusResponse } from '@/types/aistatus';

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
  AVAILABLE: 'bg-emerald-500',
  MINUTE_RATE_LIMITED: 'bg-amber-500',
  DAILY_EXHAUSTED: 'bg-red-500',
};

const aiStatusLabels: Record<AiProviderStatus, string> = {
  AVAILABLE: '정상',
  MINUTE_RATE_LIMITED: '일시 제한',
  DAILY_EXHAUSTED: '일일 한도 초과',
};

function formatMissingItems(items: PortfolioMissingItem[]) {
  if (items.length === 0) return '필수 부족 항목 없음';
  if (items.length === 1) return `${missingItemLabel[items[0]]} 필요`;
  return `${missingItemLabel[items[0]]} 외 ${items.length - 1}개`;
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

  // AI Status States
  const [aiStatus, setAiStatus] = useState<AiStatusResponse | null>(null);
  const [loadingAi, setLoadingAi] = useState(true);
  const [errorAi, setErrorAi] = useState<string | null>(null);

  // --- Data Fetching Logic ---
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

  const fetchAiStatus = useCallback(async () => {
    try {
      setErrorAi(null);
      const data = await getAiStatus();
      setAiStatus(data);
    } catch (err) {
      setErrorAi(err instanceof Error ? err.message : 'AI 상태를 불러오지 못했습니다.');
    } finally {
      setLoadingAi(false);
    }
  }, []);

  // 10초 간격 AI Status Polling
  useEffect(() => {
    fetchAiStatus();
    const intervalId = setInterval(fetchAiStatus, 10000);
    return () => clearInterval(intervalId);
  }, [fetchAiStatus, reloadToken]);

  // --- Render: Loading & Auth States (Mainly for Portfolio) ---
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

  // --- Render: Main Widget (Portfolio + AI Status Combined) ---
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
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75"></span>
              )}
              <span className={`relative inline-flex h-2 w-2 rounded-full ${aiStatus.available ? 'bg-emerald-500' : 'bg-amber-500'}`}></span>
            </span>
          )}
        </div>

        {loadingAi && <p className="mt-1 text-sm text-zinc-400">상태 확인 중...</p>}

        {errorAi && <p className="mt-1 text-sm text-red-500">{errorAi}</p>}

        {!loadingAi && !errorAi && aiStatus && (
          <div className="mt-2">
            {!aiStatus.available && aiStatus.message && (
              <div className="mb-3 rounded-lg bg-amber-50 px-3 py-2">
                <p className="text-xs font-medium text-amber-800">{aiStatus.message}</p>
                {aiStatus.estimatedWaitSeconds && (
                  <p className="mt-0.5 text-[11px] text-amber-700">
                    예상 대기 시간: <strong className="font-bold">{aiStatus.estimatedWaitSeconds}초</strong>
                  </p>
                )}
              </div>
            )}
            <dl className="grid grid-cols-2 gap-2 text-xs">
              {aiStatus.providers?.map((provider, index) => (
                <div key={index} className="flex items-center justify-between rounded-lg bg-zinc-50 px-2 py-1.5">
                  <dt className="text-zinc-500 truncate mr-2">{provider.provider}</dt>
                  <dd className="flex items-center gap-1.5 font-medium text-zinc-900 whitespace-nowrap">
                    <span className={`h-1.5 w-1.5 rounded-full ${aiStatusColors[provider.status]}`} />
                    {aiStatusLabels[provider.status]}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        )}
      </div>

      {/* 3. 하단 액션 버튼 */}
      {/* AI가 비가용 상태일 때 버튼을 비활성화하려면 아래 disable 조건을 활용하세요. 현재는 디자인만 유지했습니다. */}
      <Link
        href={nextAction.href}
        className={`mt-5 inline-flex w-full items-center justify-center rounded-full px-4 py-2.5 text-sm font-medium text-white transition-colors
          ${aiStatus && !aiStatus.available && nextAction.href === '/applications'
          ? 'bg-zinc-400 pointer-events-none' // AI 지원이 필수인 액션일 경우 비활성화 예시
          : 'bg-zinc-900 hover:bg-zinc-800'}`}
      >
        {aiStatus && !aiStatus.available && nextAction.href === '/applications'
          ? 'AI 기능 대기 중...'
          : nextAction.label}
      </Link>
    </div>
  );
}
