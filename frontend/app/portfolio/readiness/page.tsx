'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { getPortfolioReadiness } from '@/api/portfolio';
import { getRepositories } from '@/api/github';
import { getSessions } from '@/api/interview';
import { useBatchAnalysis } from '@/context/BatchAnalysisContext';
import { resolveReadinessCta } from '@/lib/readiness-cta';
import RepoSummaryModal from '@/components/RepoSummaryModal';
import type {
  PortfolioConnectionStatus,
  PortfolioMissingItem,
  PortfolioReadinessCountMetric,
  PortfolioReadinessDashboard,
  PortfolioScopeStatus,
} from '@/types/portfolio';
import type { GithubRepository } from '@/types/github';
import type { InterviewSession } from '@/types/interview';

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

function renderMetric(metric: PortfolioReadinessCountMetric, suffix = '개') {
  if (metric.status === 'not_ready' || metric.value == null) {
    return '준비 중';
  }
  return `${metric.value}${suffix}`;
}

export default function PortfolioReadinessPage() {
  const [dashboard, setDashboard] = useState<PortfolioReadinessDashboard | null>(null);
  const [sessions, setSessions] = useState<InterviewSession[] | null>(null);
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
        const [data, { data: allRepos }, interviewSessions] = await Promise.all([
          getPortfolioReadiness(),
          getRepositories({ selected: true, size: 1000 }),
          getSessions().catch(() => null),
        ]);
        setDashboard(data);
        setSessions(interviewSessions);
        setCompletedRepos(
          allRepos.filter((r) => r.hasSummary)
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

  const resolvedCta = resolveReadinessCta({
    nextRecommendedAction: dashboard.readiness.nextRecommendedAction,
    sessions,
    surface: 'dashboard',
  });
  const canStart = dashboard.readiness.canStartApplication;
  const hasMissingItems = dashboard.readiness.missingItems.length > 0;
  const summaryHeadline = hasMissingItems
    ? '지원 준비 전에 보완이 필요한 항목이 있습니다.'
    : resolvedCta.isSessionPriority
      ? '지원 준비는 가능하며, 먼저 복귀할 면접 세션이 있습니다.'
      : '현재 기준으로 지원 준비를 시작할 수 있습니다.';

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

          <div className="flex flex-wrap items-center gap-3">
            <Link
              href={resolvedCta.primaryAction.href}
              className="inline-flex items-center justify-center rounded-full bg-zinc-900 px-5 py-2.5 text-sm font-medium text-white"
            >
              {resolvedCta.primaryAction.label}
            </Link>
            {resolvedCta.secondaryAction && (
              <Link
                href={resolvedCta.secondaryAction.href}
                className="inline-flex items-center justify-center rounded-full border border-zinc-300 px-5 py-2.5 text-sm font-medium text-zinc-700"
              >
                {resolvedCta.secondaryAction.label}
              </Link>
            )}
          </div>
        </div>

        <div className="mt-6 rounded-2xl border border-zinc-100 bg-zinc-50 px-4 py-3">
          <p className="text-sm font-medium text-zinc-900">
            {summaryHeadline}
          </p>
          <p className="mt-1 text-sm text-zinc-500">{resolvedCta.primaryAction.helper}</p>
          {canStart && resolvedCta.sessionNotice && (
            <p className="mt-2 text-sm text-blue-700">{resolvedCta.sessionNotice}</p>
          )}
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
              href={resolvedCta.primaryAction.href}
              className="inline-flex items-center rounded-full bg-zinc-900 px-4 py-2 text-sm font-medium text-white"
            >
              {resolvedCta.primaryAction.label}
            </Link>
            {resolvedCta.secondaryAction && (
              <Link
                href={resolvedCta.secondaryAction.href}
                className="inline-flex items-center rounded-full border border-zinc-300 px-4 py-2 text-sm text-zinc-700"
              >
                {resolvedCta.secondaryAction.label}
              </Link>
            )}
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
          {dashboard.alerts.recentFailedJobs.status === 'not_ready' ? (
            <div className="mt-4 rounded-2xl border border-dashed border-zinc-200 bg-zinc-50 px-4 py-6 text-center">
              <p className="text-sm text-zinc-400">준비 중</p>
            </div>
          ) : !dashboard.alerts.recentFailedJobs.items?.length ? (
            <div className="mt-4 rounded-2xl border border-dashed border-zinc-200 bg-zinc-50 px-4 py-6 text-center">
              <p className="text-sm text-zinc-500">최근 7일간 실패한 작업이 없습니다.</p>
            </div>
          ) : (
            <ul className="mt-4 flex flex-col gap-2">
              {dashboard.alerts.recentFailedJobs.items.map((item, i) => (
                <li
                  key={i}
                  className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm"
                >
                  <div className="flex items-start justify-between gap-3">
                    <p className="font-medium text-red-800">{item.message}</p>
                    <span className="shrink-0 rounded bg-red-100 px-1.5 py-0.5 text-[10px] font-mono text-red-600">
                      {item.code}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-red-400">
                    {new Date(item.occurredAt).toLocaleString('ko-KR', {
                      month: 'long', day: 'numeric',
                      hour: '2-digit', minute: '2-digit',
                    })}
                  </p>
                </li>
              ))}
            </ul>
          )}
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
