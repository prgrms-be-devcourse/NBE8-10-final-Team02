'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { getPortfolioReadiness } from '@/api/portfolio';
import { getRepositories } from '@/api/github';
import { useBatchAnalysis } from '@/context/BatchAnalysisContext';
import RepoSummaryModal from '@/components/RepoSummaryModal';
import type {
  PortfolioConnectionStatus,
  PortfolioMissingItem,
  PortfolioNextRecommendedAction,
  PortfolioReadinessCountMetric,
  PortfolioReadinessDashboard,
  PortfolioScopeStatus,
} from '@/types/portfolio';
import type { GithubRepository } from '@/types/github';

const connectionStatusLabel: Record<PortfolioConnectionStatus, string> = {
  connected: '연결됨',
  not_connected: '미연결',
};

const scopeStatusLabel: Record<PortfolioScopeStatus, string> = {
  not_applicable: '해당 없음',
  public_only: 'public 조회만 가능',
  private_ready: 'private 접근 가능',
  insufficient: 'scope 부족',
};

const missingItemLabel: Record<PortfolioMissingItem, string> = {
  github_connection: 'GitHub 연결',
  selected_repository: '선택된 repository',
  document_source: '업로드 문서',
  document_extract_success: '추출 성공 문서',
};

const nextActionConfig: Record<
  PortfolioNextRecommendedAction,
  { href: string; label: string; helper: string }
> = {
  connect_github: {
    href: '/portfolio/github',
    label: 'GitHub 연결하기',
    helper: 'GitHub 연동을 먼저 완료하면 repository와 커밋 데이터를 활용할 수 있습니다.',
  },
  select_repository: {
    href: '/portfolio/repositories',
    label: 'repository 선택하기',
    helper: '활용할 repository를 선택해야 GitHub 소스를 지원 준비에 연결할 수 있습니다.',
  },
  upload_document: {
    href: '/portfolio/documents',
    label: '문서 업로드하기',
    helper: '문서 업로드 후 텍스트 추출이 완료되면 자소서와 면접 준비에 바로 사용할 수 있습니다.',
  },
  retry_document_extraction: {
    href: '/portfolio/documents',
    label: '문서 상태 확인하기',
    helper: '추출 성공 문서가 아직 없어 문서 업로드 화면에서 실패 문서를 다시 확인해야 합니다.',
  },
  start_application: {
    href: '/applications',
    label: '지원 준비 시작',
    helper: '현재 기준으로 바로 지원 준비 흐름으로 넘어갈 수 있습니다.',
  },
};

function renderMetric(metric: PortfolioReadinessCountMetric, suffix = '개') {
  if (metric.status === 'not_ready' || metric.value == null) {
    return '준비 중';
  }
  return `${metric.value}${suffix}`;
}

export default function PortfolioReadinessPage() {
  const [dashboard, setDashboard] = useState<PortfolioReadinessDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 분석 완료 repo 목록
  const [completedRepos, setCompletedRepos] = useState<GithubRepository[]>([]);
  const [summaryModalRepo, setSummaryModalRepo] = useState<GithubRepository | null>(null);

  const { cacheInvalidateKey } = useBatchAnalysis();

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError(null);

      try {
        const [data, { data: allRepos }] = await Promise.all([
          getPortfolioReadiness(),
          getRepositories({ selected: true, size: 1000 }),
        ]);
        setDashboard(data);
        setCompletedRepos(
          allRepos.filter((r) => r.analysisStatus?.status === 'COMPLETED')
        );
      } catch (e) {
        setError(e instanceof Error ? e.message : '준비 현황을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    }

    void load();
  }, [cacheInvalidateKey]); // cacheInvalidateKey 변화 시 리페치

  if (loading) {
    return (
      <main className="mx-auto max-w-5xl px-4 py-10">
        <p className="text-sm text-zinc-400">준비 현황을 불러오는 중...</p>
      </main>
    );
  }

  if (error || !dashboard) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-10">
        <div className="rounded-2xl border border-red-200 bg-red-50 px-5 py-4">
          <p className="text-sm font-medium text-red-700">
            {error ?? '준비 현황을 불러오지 못했습니다.'}
          </p>
          <Link href="/portfolio" className="mt-3 inline-block text-sm text-red-700 underline">
            포트폴리오 홈으로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

  const action = nextActionConfig[dashboard.readiness.nextRecommendedAction];
  const canStart = dashboard.readiness.canStartApplication;
  const hasMissingItems = dashboard.readiness.missingItems.length > 0;

  return (
    <main className="mx-auto max-w-5xl px-4 py-10">
      <section className="rounded-3xl border border-zinc-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div className="flex items-center gap-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-zinc-900 text-lg font-semibold text-white">
              {dashboard.profile.displayName.slice(0, 1).toUpperCase()}
            </div>
            <div>
              <p className="text-sm text-zinc-500">준비 현황 대시보드</p>
              <h1 className="text-2xl font-semibold text-zinc-900">
                {dashboard.profile.displayName}님의 준비 현황
              </h1>
              <p className="mt-1 text-sm text-zinc-500">
                {dashboard.profile.email ?? '이메일 정보 없음'}
              </p>
            </div>
          </div>

          <Link
            href={action.href}
            className="inline-flex items-center justify-center rounded-full bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white"
          >
            {action.label}
          </Link>
        </div>

        <div className="mt-6 rounded-2xl border border-zinc-100 bg-zinc-50 px-4 py-3">
          <p className="text-sm font-medium text-zinc-900">
            {canStart ? '현재 기준으로 지원 준비를 시작할 수 있습니다.' : '지원 준비 전에 보완이 필요한 항목이 있습니다.'}
          </p>
          <p className="mt-1 text-sm text-zinc-500">{action.helper}</p>
        </div>
      </section>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        <section className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-zinc-900">GitHub</h2>
            <Link href="/portfolio/github" className="text-sm text-zinc-500 underline">
              연결 관리
            </Link>
          </div>
          <dl className="mt-4 space-y-3 text-sm">
            <div className="flex items-center justify-between gap-4">
              <dt className="text-zinc-500">연결 상태</dt>
              <dd className="font-medium text-zinc-900">
                {connectionStatusLabel[dashboard.github.connectionStatus]}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-zinc-500">scope 상태</dt>
              <dd className="font-medium text-zinc-900">
                {scopeStatusLabel[dashboard.github.scopeStatus]}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-zinc-500">선택된 repository</dt>
              <dd className="font-medium text-zinc-900">
                {dashboard.github.selectedRepositoryCount}개
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-zinc-500">최근 수집 commit</dt>
              <dd className="font-medium text-zinc-900">
                {renderMetric(dashboard.github.recentCollectedCommitCount, '건')}
              </dd>
            </div>
          </dl>
        </section>

        <section className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-zinc-900">문서</h2>
            <Link href="/portfolio/documents" className="text-sm text-zinc-500 underline">
              업로드 관리
            </Link>
          </div>
          <dl className="mt-4 space-y-3 text-sm">
            <div className="flex items-center justify-between gap-4">
              <dt className="text-zinc-500">업로드 문서</dt>
              <dd className="font-medium text-zinc-900">
                {dashboard.documents.totalCount}개
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-zinc-500">추출 성공</dt>
              <dd className="font-medium text-zinc-900">
                {dashboard.documents.extractSuccessCount}개
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-zinc-500">추출 실패</dt>
              <dd className="font-medium text-zinc-900">
                {dashboard.documents.extractFailedCount}개
              </dd>
            </div>
          </dl>
        </section>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-[1.5fr_1fr]">
        <section className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-zinc-900">부족한 항목과 다음 행동</h2>
          {hasMissingItems ? (
            <ul className="mt-4 flex flex-wrap gap-2">
              {dashboard.readiness.missingItems.map((item) => (
                <li
                  key={item}
                  className="rounded-full border border-zinc-200 bg-zinc-50 px-3 py-1 text-sm text-zinc-700"
                >
                  {missingItemLabel[item]}
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-4 text-sm text-zinc-500">
              현재 기준으로 필수 부족 항목은 없습니다.
            </p>
          )}

          <div className="mt-5 flex flex-wrap gap-3">
            <Link
              href={action.href}
              className="inline-flex items-center rounded-full bg-zinc-900 px-4 py-2 text-sm font-medium text-white"
            >
              {action.label}
            </Link>
            <Link
              href="/portfolio/repositories"
              className="inline-flex items-center rounded-full border border-zinc-300 px-4 py-2 text-sm text-zinc-700"
            >
              repository 보기
            </Link>
            <Link
              href="/portfolio/documents"
              className="inline-flex items-center rounded-full border border-zinc-300 px-4 py-2 text-sm text-zinc-700"
            >
              문서 보기
            </Link>
          </div>
        </section>

        <section className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-zinc-900">최근 실패 작업 알림</h2>
          <div className="mt-4 rounded-2xl border border-dashed border-zinc-200 bg-zinc-50 px-4 py-6 text-center">
            <p className="text-sm font-medium text-zinc-900">
              {dashboard.alerts.recentFailedJobs.status === 'not_ready'
                ? '준비 중'
                : `${dashboard.alerts.recentFailedJobs.items?.length ?? 0}건`}
            </p>
            <p className="mt-1 text-sm text-zinc-500">
              최근 실패 작업 통합 집계는 이번 v1 범위에서 아직 제공하지 않습니다.
            </p>
          </div>
        </section>
      </div>

      {/* 분석 완료 Repo 결과 검수 섹션 (Step 8) */}
      {completedRepos.length > 0 && (
        <section className="mt-6 rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-zinc-900">분석 완료 Repository</h2>
          <p className="mt-1 text-sm text-zinc-500">
            클릭하면 AI 분석 결과를 확인할 수 있습니다.
          </p>
          <ul className="mt-4 flex flex-col gap-2">
            {completedRepos.map((repo) => (
              <li key={repo.id}>
                <button
                  onClick={() => setSummaryModalRepo(repo)}
                  className="w-full rounded-xl border border-zinc-100 bg-zinc-50 px-4 py-3 text-left hover:bg-zinc-100 transition-colors cursor-pointer"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-zinc-900 truncate">{repo.fullName}</p>
                      {repo.analysisStatus?.completedAt && (
                        <p className="text-xs text-zinc-400 mt-0.5">
                          분석 완료: {new Date(repo.analysisStatus.completedAt).toLocaleDateString('ko-KR', {
                            year: 'numeric', month: 'long', day: 'numeric',
                          })}
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {repo.language && (
                        <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500">{repo.language}</span>
                      )}
                      <span className="text-xs text-indigo-600 font-medium">결과 보기 →</span>
                    </div>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="mt-8 flex flex-wrap gap-4 text-sm">
        <Link href="/portfolio" className="text-zinc-500 underline">
          ← 포트폴리오 홈
        </Link>
        <Link href="/portfolio/github" className="text-zinc-500 underline">
          GitHub 연결
        </Link>
        <Link href="/portfolio/documents" className="text-zinc-500 underline">
          문서 업로드
        </Link>
        <Link href="/applications" className="text-zinc-900 underline">
          지원 준비
        </Link>
      </div>

      {/* 분석 결과 모달 */}
      {summaryModalRepo && (
        <RepoSummaryModal
          repositoryId={summaryModalRepo.id}
          repoFullName={summaryModalRepo.fullName}
          onClose={() => setSummaryModalRepo(null)}
        />
      )}
    </main>
  );
}
