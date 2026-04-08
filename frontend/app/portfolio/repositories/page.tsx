'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  getRepositories,
  saveRepositorySelection,
  syncCommits,
  getContributions,
  saveContribution,
  addContributionByUrl,
  removeRepository,
  refreshGithubConnection,
} from '@/api/github';
import { useBatchAnalysis } from '@/context/BatchAnalysisContext';
import ResyncConfirmModal from '@/components/ResyncConfirmModal';
import type { GithubRepository, ContributedRepo, RepoSyncStatus } from '@/types/github';
import type { Pagination } from '@/types/common';

// ── 유틸 ──────────────────────────────────────────────────────────────

function formatPushedAt(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const diff = Date.now() - new Date(iso).getTime();
  const min  = Math.floor(diff / 60_000);
  const hr   = Math.floor(diff / 3_600_000);
  const day  = Math.floor(diff / 86_400_000);
  const mon  = Math.floor(day / 30);
  if (min < 60)  return `${min}분 전`;
  if (hr  < 24)  return `${hr}시간 전`;
  if (day < 30)  return `${day}일 전`;
  if (mon < 12)  return `${mon}개월 전`;
  return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long' });
}

function formatDate(iso: string | null | undefined): string | null {
  if (!iso) return null;
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
  });
}

const VISIBILITY_STYLE: Record<string, string> = {
  public: 'bg-green-100 text-green-700',
  private: 'bg-yellow-100 text-yellow-700',
  internal: 'bg-blue-100 text-blue-700',
};

type Tab = 'owned' | 'contributed';

// ── 메인 페이지 ───────────────────────────────────────────────────────

export default function RepositoriesPage() {
  const [activeTab, setActiveTab] = useState<Tab>('owned');

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="mb-1 text-xl font-semibold">Repository 관리</h1>
      <p className="mb-5 text-sm text-zinc-500">
        포트폴리오에 활용할 repository를 선택하고 분석을 시작하세요.
      </p>

      <div className="mb-6 flex rounded border border-zinc-200">
        <button
          onClick={() => setActiveTab('owned')}
          className={`flex-1 py-2 text-sm font-medium transition-colors cursor-pointer ${
            activeTab === 'owned'
              ? 'bg-zinc-900 text-white'
              : 'bg-white text-zinc-600 hover:bg-zinc-50'
          }`}
        >
          내 repository
        </button>
        <button
          onClick={() => setActiveTab('contributed')}
          className={`flex-1 py-2 text-sm font-medium transition-colors cursor-pointer ${
            activeTab === 'contributed'
              ? 'bg-zinc-900 text-white'
              : 'bg-white text-zinc-600 hover:bg-zinc-50'
          }`}
        >
          기여한 repository
        </button>
      </div>

      {activeTab === 'owned' ? <OwnedTab /> : <ContributedTab />}

      <div className="mt-6 flex gap-4 text-sm text-zinc-400">
        <Link href="/portfolio/github" className="underline">
          ← GitHub 연결 변경
        </Link>
        <Link href="/portfolio" className="underline">
          포트폴리오 홈 →
        </Link>
      </div>
    </main>
  );
}

// ── 탭 1 — 내 repository ─────────────────────────────────────────────

function OwnedTab() {
  const router = useRouter();
  const { startBatch, preSelectedIds, clearPreSelected, activeBatch } = useBatchAnalysis();

  const [repos, setRepos] = useState<GithubRepository[]>([]);
  const [repoInfoMap, setRepoInfoMap] = useState<Map<number, GithubRepository>>(new Map());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [currentPage, setCurrentPage] = useState(1);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [autoRefreshDone, setAutoRefreshDone] = useState(false);
  const [startingBatch, setStartingBatch] = useState(false);
  const [batchError, setBatchError] = useState<string | null>(null);

  // Re-sync 모달 상태
  const [resyncTarget, setResyncTarget] = useState<GithubRepository | null>(null);
  const [reSyncing, setReSyncing] = useState<number | null>(null);

  function applyStatusesFromRepos(data: GithubRepository[]) {
    // 분석 상태는 repo 객체에서 직접 읽으므로 별도 상태 불필요
    void data;
  }

  const init = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [{ data, pagination: pag }, { data: allSelected }] = await Promise.all([
        getRepositories({ page: 1, size: 10 }),
        getRepositories({ selected: true, size: 1000 }),
      ]);
      setRepos(data);
      setPagination(pag);
      setCurrentPage(1);
      setSelectedIds(new Set(allSelected.map((r) => r.id)));
      setRepoInfoMap(new Map([
        ...allSelected.map((r) => [r.id, r] as [number, GithubRepository]),
        ...data.map((r) => [r.id, r] as [number, GithubRepository]),
      ]));
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { init(); }, [init]);

  // 재시도 흐름: context에서 preSelectedIds 받아 체크 상태 반영
  useEffect(() => {
    if (preSelectedIds.length === 0) return;
    setSelectedIds((prev) => {
      const next = new Set(prev);
      preSelectedIds.forEach((id) => next.add(id));
      return next;
    });
    clearPreSelected();
  }, [preSelectedIds, clearPreSelected]);

  useEffect(() => {
    if (!loading && !loadError && repos.length === 0 && !autoRefreshDone && !refreshing) {
      setAutoRefreshDone(true);
      handleRefresh();
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, repos.length, loadError]);

  async function loadPage(page: number) {
    setLoading(true);
    setLoadError(null);
    try {
      const { data, pagination: pag } = await getRepositories({ page, size: 10 });
      setRepos(data);
      setPagination(pag);
      setCurrentPage(page);
      applyStatusesFromRepos(data);
      setRepoInfoMap((prev) => {
        const next = new Map(prev);
        data.forEach((r) => next.set(r.id, r));
        return next;
      });
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }

  const refreshRepos = useCallback(async () => {
    try {
      const { data, pagination: pag } = await getRepositories({ page: currentPage, size: 10 });
      setRepos(data);
      setPagination(pag);
      setRepoInfoMap((prev) => {
        const next = new Map(prev);
        data.forEach((r) => next.set(r.id, r));
        return next;
      });
    } catch { /* 폴링 실패 무시 */ }
  }, [currentPage]);

  // 현재 페이지에 PENDING/IN_PROGRESS 상태인 repo가 있으면 3초 폴링
  const hasActiveAnalysis = repos.some(
    (r) => r.analysisStatus?.status === 'PENDING' || r.analysisStatus?.status === 'IN_PROGRESS'
  );

  useEffect(() => {
    if (!hasActiveAnalysis) return;
    const id = setInterval(refreshRepos, 3000);
    return () => clearInterval(id);
  }, [hasActiveAnalysis, refreshRepos]);

  async function toggleSelect(repoId: number) {
    if (togglingId !== null) return;
    const prevIds = new Set(selectedIds);
    const newIds = new Set(selectedIds);
    newIds.has(repoId) ? newIds.delete(repoId) : newIds.add(repoId);
    setSelectedIds(newIds);
    setTogglingId(repoId);
    setToggleError(null);
    try {
      await saveRepositorySelection(Array.from(newIds));
      await refreshRepos();
    } catch (err) {
      setSelectedIds(prevIds);
      setToggleError(err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.');
    } finally {
      setTogglingId(null);
    }
  }

  async function handleRefresh() {
    setRefreshing(true);
    setRefreshError(null);
    try {
      await refreshGithubConnection();
      await init();
    } catch (err) {
      setRefreshError(err instanceof Error ? err.message : 'repository 목록을 가져오지 못했습니다.');
    } finally {
      setRefreshing(false);
    }
  }

  // ── 일괄 분석 시작 ──

  // 선택된 repo 중 분석 대상: summary가 없는 것 (커밋 동기화 여부는 무관 — 자동 처리)
  function getAnalyzableIds(): number[] {
    return Array.from(selectedIds).filter((id) => {
      const repo = repoInfoMap.get(id);
      if (!repo) return false;
      return !repo.hasSummary;
    });
  }

  async function handleStartBatch() {
    const ids = getAnalyzableIds();
    if (ids.length === 0) return;
    setStartingBatch(true);
    setBatchError(null);
    try {
      // 커밋 미동기화 repo는 병렬로 sync 먼저 수행, 이미 된 것은 백엔드에서 skip
      const unsynced = ids.filter((id) => !repoInfoMap.get(id)?.hasCommits);
      if (unsynced.length > 0) {
        await Promise.all(unsynced.map((id) => syncCommits(id).catch(() => {})));
      }
      const repoNames: Record<number, string> = {};
      ids.forEach((id) => {
        const repo = repoInfoMap.get(id);
        if (repo) repoNames[id] = repo.fullName;
      });
      await startBatch(ids, repoNames);
      router.push('/portfolio/documents');
    } catch (err) {
      setBatchError(err instanceof Error ? err.message : '분석 시작 중 오류가 발생했습니다.');
      setStartingBatch(false);
    }
  }

  // ── Re-sync ──

  async function handleResyncConfirm(repo: GithubRepository) {
    setResyncTarget(null);
    setReSyncing(repo.id);
    try {
      await syncCommits(repo.id);
      await startBatch([repo.id], { [repo.id]: repo.fullName });
      router.push('/portfolio/documents');
    } catch (err) {
      setBatchError(err instanceof Error ? err.message : '업데이트 시작 중 오류가 발생했습니다.');
    } finally {
      setReSyncing(null);
    }
  }

  // ── 렌더 ──

  if (loading) return <p className="text-sm text-zinc-400">repository 목록을 불러오는 중...</p>;

  if (loadError) return (
    <div>
      <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</div>
      <button onClick={init} className="mt-4 text-sm underline text-zinc-500 cursor-pointer">다시 시도</button>
    </div>
  );

  if (repos.length === 0 && currentPage === 1) return (
    <div>
      <p className="mb-4 text-sm text-zinc-500">연결된 GitHub 계정이 없거나 repository가 없습니다.</p>
      {refreshError && <p className="mb-3 text-sm text-red-600">{refreshError}</p>}
      <div className="flex gap-3">
        <button
          onClick={handleRefresh}
          disabled={refreshing}
          className="rounded border border-zinc-300 px-3 py-1.5 text-sm text-zinc-600 hover:bg-zinc-50 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {refreshing ? '가져오는 중...' : 'GitHub에서 다시 가져오기'}
        </button>
        <Link href="/portfolio/github" className="text-sm underline text-zinc-500 self-center">GitHub 연결 변경</Link>
      </div>
    </div>
  );

  const analyzableIds = getAnalyzableIds();

  return (
    <div>
      {/* 상단 안내 */}
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-zinc-500">
          분석할 repository를 선택하세요.
          {selectedIds.size > 0 && (
            <span className="ml-1 font-medium text-zinc-800">{selectedIds.size}개 선택됨</span>
          )}
        </p>
        <div className="flex items-center gap-3">
          {pagination && pagination.totalElements > 0 && (
            <p className="text-xs text-zinc-400">전체 {pagination.totalElements}개</p>
          )}
          <div className="relative group">
            <button
              onClick={handleRefresh}
              disabled={refreshing}
              className="text-xs text-zinc-400 underline hover:text-zinc-600 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {refreshing ? '가져오는 중...' : 'GitHub 동기화'}
            </button>
            <div className="pointer-events-none absolute right-0 top-6 z-10 whitespace-nowrap rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs text-zinc-600 shadow-md opacity-0 group-hover:opacity-100 transition-opacity duration-150">
              repository가 보이지 않으면 눌러보세요.
            </div>
          </div>
        </div>
      </div>

      {refreshError && (
        <div className="mb-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">{refreshError}</div>
      )}
      {toggleError && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{toggleError}</div>
      )}

      {/* Repo 목록 */}
      <ul className="flex flex-col gap-3">
        {repos.map((repo) => {
          const isSelected = selectedIds.has(repo.id);
          const isToggling = togglingId === repo.id;
          const isCompleted = repo.hasSummary;
          const isDisabled = isCompleted; // 완료된 repo는 체크박스 비활성화
          const isReSyncing = reSyncing === repo.id;
          const isUpToDate =
            !!repo.analysisStatus?.completedAt &&
            !!repo.pushedAt &&
            new Date(repo.analysisStatus.completedAt) >= new Date(repo.pushedAt);

          return (
            <li
              key={repo.id}
              className={`rounded border px-4 py-3 transition-opacity ${
                isDisabled
                  ? 'border-zinc-200 bg-zinc-50'
                  : isSelected
                  ? 'border-zinc-300 cursor-pointer hover:bg-zinc-50'
                  : 'border-zinc-200 opacity-50 cursor-pointer hover:bg-zinc-50'
              } ${isToggling ? 'pointer-events-none' : ''}`}
              onClick={() => !isDisabled && toggleSelect(repo.id)}
            >
              <div className="flex items-start gap-3">
                {/* 체크박스 */}
                <div
                  className="mt-0.5 shrink-0"
                  onClick={(e) => e.stopPropagation()}
                >
                  <input
                    type="checkbox"
                    checked={isSelected}
                    disabled={isDisabled || isToggling}
                    onChange={() => !isDisabled && toggleSelect(repo.id)}
                    className="cursor-pointer disabled:cursor-not-allowed"
                    title={isDisabled ? '분석이 완료된 repository입니다. 업데이트 버튼을 사용하세요.' : undefined}
                  />
                </div>

                {/* 정보 */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <a
                      href={repo.htmlUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      className="text-sm font-medium text-zinc-900 hover:underline truncate"
                    >
                      {repo.fullName}
                    </a>
                    <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${VISIBILITY_STYLE[repo.visibility] ?? 'bg-zinc-100 text-zinc-600'}`}>
                      {repo.visibility}
                    </span>
                    {repo.ownerType === 'owner' && (
                      <span className="rounded bg-zinc-800 px-1.5 py-0.5 text-xs font-medium text-white">owner</span>
                    )}
                    {repo.ownerType === 'collaborator' && (
                      <span className="rounded bg-indigo-100 px-1.5 py-0.5 text-xs font-medium text-indigo-700">collaborator</span>
                    )}
                    {repo.language && (
                      <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500">{repo.language}</span>
                    )}
                  </div>

                  {formatPushedAt(repo.pushedAt) && (
                    <p className="mt-0.5 text-xs text-zinc-400">{formatPushedAt(repo.pushedAt)} 업데이트</p>
                  )}

                  {/* 분석 상태 배지 — 배치 진행 중이면 live status 우선 */}
                  {(() => {
                    const liveStatus = activeBatch?.statuses[repo.id] ?? repo.analysisStatus;
                    return liveStatus ? <AnalysisStatusBadge status={liveStatus} /> : null;
                  })()}
                </div>

                {/* Re-sync 버튼 (COMPLETED repo 전용, 최신 상태면 숨김) */}
                {isCompleted && !isUpToDate && (
                  <div className="shrink-0 flex flex-col items-end gap-1" onClick={(e) => e.stopPropagation()}>
                    {repo.analysisStatus?.completedAt && (
                      <p className="text-xs text-zinc-400">{formatDate(repo.analysisStatus.completedAt)}</p>
                    )}
                    <button
                      onClick={() => setResyncTarget(repo)}
                      disabled={isReSyncing}
                      title="최신 내용으로 업데이트 (Re-sync)"
                      className="rounded border border-zinc-300 px-2 py-1 text-xs text-zinc-500 hover:bg-zinc-100 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {isReSyncing ? '업데이트 중...' : '🔄 업데이트'}
                    </button>
                  </div>
                )}
              </div>
            </li>
          );
        })}
      </ul>

      {/* 페이지네이션 */}
      {pagination && pagination.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3">
          <button
            onClick={() => loadPage(currentPage - 1)}
            disabled={currentPage <= 1 || loading}
            className="rounded border border-zinc-300 px-3 py-1.5 text-sm text-zinc-600 hover:bg-zinc-50 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
          >
            이전
          </button>
          <span className="text-sm text-zinc-500">{currentPage} / {pagination.totalPages}</span>
          <button
            onClick={() => loadPage(currentPage + 1)}
            disabled={currentPage >= pagination.totalPages || loading}
            className="rounded border border-zinc-300 px-3 py-1.5 text-sm text-zinc-600 hover:bg-zinc-50 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
          >
            다음
          </button>
        </div>
      )}

      {/* 오류 메시지 */}
      {batchError && (
        <div className="mt-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{batchError}</div>
      )}

      {/* 일괄 분석 시작 버튼 */}
      <div className="mt-6 border-t border-zinc-100 pt-5">
        <button
          onClick={handleStartBatch}
          disabled={analyzableIds.length === 0 || startingBatch}
          className="w-full rounded-full bg-zinc-900 py-3 text-sm font-semibold text-white hover:bg-zinc-700 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          {startingBatch
            ? '분석 시작 중...'
            : analyzableIds.length > 0
            ? `분석 시작 (${analyzableIds.length}개 repo)`
            : '분석할 repository를 선택하세요'}
        </button>
        {analyzableIds.length > 0 && (
          <p className="mt-2 text-center text-xs text-zinc-400">
            분석 요청 후 문서 업로드 화면으로 이동하며, 백그라운드에서 계속 진행됩니다.
          </p>
        )}
      </div>

      {/* Re-sync 확인 모달 */}
      {resyncTarget && (
        <ResyncConfirmModal
          repoFullName={resyncTarget.fullName}
          lastAnalyzedAt={resyncTarget.analysisStatus?.completedAt ?? null}
          onConfirm={() => handleResyncConfirm(resyncTarget)}
          onCancel={() => setResyncTarget(null)}
        />
      )}
    </div>
  );
}

// ── 탭 2 — 기여한 repository ─────────────────────────────────────────

function ContributedTab() {
  const [contributions, setContributions] = useState<ContributedRepo[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [yearsOffset, setYearsOffset] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [noMoreData, setNoMoreData] = useState(false);

  const [savingId, setSavingId] = useState<number | null>(null);
  const [saveErrors, setSaveErrors] = useState<Record<number, string>>({});

  const [urlInput, setUrlInput] = useState('');
  const [urlLoading, setUrlLoading] = useState(false);
  const [urlError, setUrlError] = useState<string | null>(null);
  const [urlSuccess, setUrlSuccess] = useState<string | null>(null);

  useEffect(() => {
    handleLoad(0, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleLoad(offset: number, append: boolean) {
    if (offset === 0) setLoading(true);
    else setLoadingMore(true);
    setLoadError(null);

    try {
      const data = await getContributions(offset);
      if (data.length === 0) {
        setNoMoreData(true);
      } else {
        setContributions((prev) => {
          if (!append) return data;
          const existing = new Set(prev.map((r) => r.githubRepoId));
          return [...prev, ...data.filter((r) => !existing.has(r.githubRepoId))];
        });
        setYearsOffset(offset);
      }
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '기여 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }

  async function handleCardClick(repo: ContributedRepo) {
    if (savingId !== null) return;
    setSaveErrors((prev) => { const n = { ...prev }; delete n[repo.githubRepoId]; return n; });
    setSavingId(repo.githubRepoId);

    try {
      if (!repo.alreadySaved) {
        const saved = await saveContribution({
          githubRepoId: repo.githubRepoId,
          nameWithOwner: repo.nameWithOwner,
          url: repo.url,
          language: repo.language,
          repoSizeKb: repo.repoSizeKb,
        });
        setContributions((prev) =>
          prev.map((r) =>
            r.githubRepoId === repo.githubRepoId
              ? { ...r, alreadySaved: true, repositoryId: saved.id }
              : r
          )
        );
      } else if (repo.repositoryId !== null) {
        await removeRepository(repo.repositoryId);
        setContributions((prev) =>
          prev.map((r) =>
            r.githubRepoId === repo.githubRepoId
              ? { ...r, alreadySaved: false, repositoryId: null }
              : r
          )
        );
      }
    } catch (err) {
      setSaveErrors((prev) => ({
        ...prev,
        [repo.githubRepoId]: err instanceof Error ? err.message : '처리 중 오류가 발생했습니다.',
      }));
    } finally {
      setSavingId(null);
    }
  }

  async function handleAddByUrl(e: React.FormEvent) {
    e.preventDefault();
    if (!urlInput.trim()) return;
    setUrlLoading(true);
    setUrlError(null);
    setUrlSuccess(null);

    try {
      const saved = await addContributionByUrl(urlInput.trim());
      setUrlSuccess(`${saved.fullName} 이(가) 추가되었습니다.`);
      setUrlInput('');
    } catch (err) {
      setUrlError(err instanceof Error ? err.message : '추가 중 오류가 발생했습니다.');
    } finally {
      setUrlLoading(false);
    }
  }

  if (loadError) return (
    <div>
      <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</div>
      <button onClick={() => handleLoad(0, false)} className="mt-4 text-sm underline text-zinc-500 cursor-pointer">다시 시도</button>
    </div>
  );

  return (
    <div>
      <p className="mb-4 text-sm text-zinc-500">
        최근 {yearsOffset + 1}년간 커밋을 기여한 public repository 목록입니다.
        클릭하면 포트폴리오에 추가됩니다.
      </p>

      {contributions.length === 0 ? (
        loading
          ? <p className="text-sm text-zinc-400 mb-4">불러오는 중...</p>
          : <p className="text-sm text-zinc-400 mb-4">이 기간에 기여한 repository가 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-2 mb-4">
          {contributions.map((repo) => {
            const isSaving = savingId === repo.githubRepoId;
            const saveError = saveErrors[repo.githubRepoId];

            return (
              <li
                key={repo.githubRepoId}
                onClick={() => handleCardClick(repo)}
                className={`rounded border px-4 py-3 transition-opacity select-none ${
                  isSaving
                    ? 'border-zinc-200 pointer-events-none opacity-50'
                    : 'border-zinc-200 cursor-pointer hover:bg-zinc-50'
                }`}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <a href={repo.url} target="_blank" rel="noopener noreferrer"
                        onClick={(e) => e.stopPropagation()}
                        className="text-sm font-medium text-zinc-900 hover:underline truncate">
                        {repo.nameWithOwner}
                      </a>
                      {repo.language && (
                        <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500">{repo.language}</span>
                      )}
                    </div>
                    <p className="mt-0.5 text-xs text-zinc-400">
                      기여 커밋 {repo.contributionCount}개
                      {repo.repoSizeKb != null && ` · ${(repo.repoSizeKb / 1024).toFixed(1)} MB`}
                    </p>
                    {saveError && <p className="mt-1 text-xs text-red-600">{saveError}</p>}
                  </div>
                  <span className={`shrink-0 text-xs font-medium ${
                    isSaving ? 'text-zinc-400' : repo.alreadySaved ? 'text-green-600' : 'text-indigo-600'
                  }`}>
                    {isSaving ? '처리 중...' : repo.alreadySaved ? '추가됨' : '+ 추가'}
                  </span>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {!loading && !noMoreData ? (
        <button
          onClick={() => handleLoad(yearsOffset + 1, true)}
          disabled={loadingMore}
          className="mb-6 w-full rounded border border-zinc-300 py-2 text-sm text-zinc-600 hover:bg-zinc-50 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loadingMore ? '불러오는 중...' : `이전 기여 더 불러오기 (${yearsOffset + 1}~${yearsOffset + 2}년 전)`}
        </button>
      ) : (
        !loading && <p className="mb-6 text-center text-xs text-zinc-400">더 이상 불러올 기여 이력이 없습니다.</p>
      )}

      <div className="rounded border border-zinc-200 p-4">
        <p className="mb-2 text-sm font-medium text-zinc-700">repo URL로 직접 추가</p>
        <p className="mb-3 text-xs text-zinc-500">
          목록에 없는 기여 repo가 있다면 URL을 입력하세요.
        </p>
        <form onSubmit={handleAddByUrl} className="flex gap-2">
          <input
            type="text"
            value={urlInput}
            onChange={(e) => { setUrlInput(e.target.value); setUrlError(null); setUrlSuccess(null); }}
            placeholder="https://github.com/owner/repo"
            disabled={urlLoading}
            className="flex-1 rounded border border-zinc-300 px-3 py-1.5 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-50"
          />
          <button
            type="submit"
            disabled={urlLoading || !urlInput.trim()}
            className="rounded bg-zinc-900 px-4 py-1.5 text-sm font-medium text-white cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {urlLoading ? '확인 중...' : '추가'}
          </button>
        </form>
        {urlError && <p className="mt-2 text-xs text-red-600">{urlError}</p>}
        {urlSuccess && <p className="mt-2 text-xs text-green-600">{urlSuccess}</p>}
      </div>
    </div>
  );
}

// ── 분석 상태 배지 ────────────────────────────────────────────────────

const STEP_LABEL: Record<string, string> = {
  significance_check: '변경 감지 중',
  clone: '저장소 복제 중',
  analysis: '코드 분석 중',
  summary: 'AI 요약 생성 중',
};

function AnalysisStatusBadge({ status }: { status: RepoSyncStatus }) {
  if (status.status === 'COMPLETED') {
    return <p className="mt-1 text-xs text-indigo-700 font-medium">✓ 포트폴리오 분석 완료</p>;
  }
  if (status.status === 'SKIPPED') {
    return <p className="mt-1 text-xs text-zinc-400">{status.skipReason ?? '변경 사항이 충분하지 않아 분석을 건너뛰었습니다.'}</p>;
  }
  if (status.status === 'FAILED') {
    return <p className="mt-1 text-xs text-red-600">분석 실패{status.error ? `: ${status.error}` : ''}</p>;
  }
  if (status.status === 'IN_PROGRESS' || status.status === 'PENDING') {
    const stepLabel = status.step ? STEP_LABEL[status.step] ?? status.step : '준비 중';
    return <p className="mt-1 text-xs text-indigo-500 animate-pulse">⟳ {stepLabel}...</p>;
  }
  return null;
}
