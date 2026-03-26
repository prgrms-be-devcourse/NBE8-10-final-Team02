'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import Link from 'next/link';
import {
  getRepositories,
  saveRepositorySelection,
  syncCommits,
  analyzeRepository,
  cancelAnalysis,
  getContributions,
  saveContribution,
  addContributionByUrl,
} from '@/api/github';
import type { GithubRepository, RepoSyncStatus, ContributedRepo } from '@/types/github';

// visibility 배지 색상
const VISIBILITY_STYLE: Record<string, string> = {
  public: 'bg-green-100 text-green-700',
  private: 'bg-yellow-100 text-yellow-700',
  internal: 'bg-blue-100 text-blue-700',
};

// ── 탭 타입 ───────────────────────────────────────────
type Tab = 'owned' | 'contributed';

// ══════════════════════════════════════════════════════
// 메인 페이지
// ══════════════════════════════════════════════════════
export default function RepositoriesPage() {
  const [activeTab, setActiveTab] = useState<Tab>('owned');

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="mb-1 text-xl font-semibold">Repository 관리</h1>
      <p className="mb-5 text-sm text-zinc-500">
        포트폴리오에 활용할 repository를 선택하고 커밋을 수집하세요.
      </p>

      {/* 탭 */}
      <div className="mb-6 flex rounded border border-zinc-200">
        <button
          onClick={() => setActiveTab('owned')}
          className={`flex-1 py-2 text-sm font-medium transition-colors ${
            activeTab === 'owned'
              ? 'bg-zinc-900 text-white'
              : 'bg-white text-zinc-600 hover:bg-zinc-50'
          }`}
        >
          내 repository
        </button>
        <button
          onClick={() => setActiveTab('contributed')}
          className={`flex-1 py-2 text-sm font-medium transition-colors ${
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

// ══════════════════════════════════════════════════════
// 탭 1 — 내 repository (기존 기능)
// ══════════════════════════════════════════════════════
function OwnedTab() {
  const [repos, setRepos] = useState<GithubRepository[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [syncingIds, setSyncingIds] = useState<Set<number>>(new Set());
  const [syncErrors, setSyncErrors] = useState<Record<number, string>>({});
  const [analysisStatuses, setAnalysisStatuses] = useState<Record<number, RepoSyncStatus>>({});
  const [analyzingIds, setAnalyzingIds] = useState<Set<number>>(new Set());
  const [analyzeErrors, setAnalyzeErrors] = useState<Record<number, string>>({});

  // 분석 상태를 응답에서 추출해 analysisStatuses에 반영 (repos 갱신 시 공통 사용)
  function applyStatusesFromRepos(data: GithubRepository[]) {
    const statuses: Record<number, RepoSyncStatus> = {};
    data.forEach((r) => { if (r.analysisStatus) statuses[r.id] = r.analysisStatus; });
    setAnalysisStatuses(statuses);
  }

  // 초기 로드: 로딩 UI 포함, selectedIds도 서버 상태로 초기화
  const loadRepos = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const { data } = await getRepositories({ page: 1, size: 100 });
      setRepos(data);
      setSelectedIds(new Set(data.filter((r) => r.isSelected).map((r) => r.id)));
      applyStatusesFromRepos(data);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  // 폴링용 갱신: 로딩 UI 없음, selectedIds는 건드리지 않음 (사용자 미저장 선택 유지)
  const refreshRepos = useCallback(async () => {
    try {
      const { data } = await getRepositories({ page: 1, size: 100 });
      setRepos(data);
      applyStatusesFromRepos(data);
    } catch { /* 폴링 실패 무시 */ }
  }, []);

  useEffect(() => { loadRepos(); }, [loadRepos]);

  // PENDING/IN_PROGRESS인 repo가 있을 때만 3초 폴링
  // 모두 종료(COMPLETED/SKIPPED/FAILED/null)되면 자동으로 폴링 중단
  const hasActiveAnalysis =
    analyzingIds.size > 0 ||
    Object.values(analysisStatuses).some(
      (s) => s.status === 'PENDING' || s.status === 'IN_PROGRESS'
    );

  useEffect(() => {
    if (!hasActiveAnalysis) return;
    const id = setInterval(refreshRepos, 3000);
    return () => clearInterval(id);
  }, [hasActiveAnalysis, refreshRepos]);

  async function handleAnalyze(repoId: number) {
    setAnalyzingIds((prev) => new Set(prev).add(repoId));
    setAnalyzeErrors((prev) => { const n = { ...prev }; delete n[repoId]; return n; });
    try {
      const status = await analyzeRepository(repoId);
      if (status) setAnalysisStatuses((prev) => ({ ...prev, [repoId]: status }));
      // API 응답 후 analysisStatuses가 PENDING/IN_PROGRESS를 가지므로 analyzingIds 제거
      // hasActiveAnalysis는 analysisStatuses 기준으로 폴링을 계속 유지함
      setAnalyzingIds((prev) => { const n = new Set(prev); n.delete(repoId); return n; });
    } catch (err) {
      // 409: 이미 진행 중인 분석 → 폴링이 상태를 가져올 것이므로 에러 무시
      if (err instanceof Error && err.message.includes('409')) {
        setAnalyzingIds((prev) => { const n = new Set(prev); n.delete(repoId); return n; });
        return;
      }
      const msg = err instanceof Error ? err.message : '분석 시작 중 오류가 발생했습니다.';
      setAnalyzeErrors((prev) => ({ ...prev, [repoId]: msg }));
      setAnalyzingIds((prev) => { const n = new Set(prev); n.delete(repoId); return n; });
    }
  }

  async function handleCancelAnalysis(repoId: number) {
    try {
      await cancelAnalysis(repoId);
    } catch { /* 취소 실패는 무시 — 폴링이 최종 상태를 반영함 */ }
    // 로컬 상태 즉시 업데이트 (낙관적), 폴링이 실제 FAILED 상태를 곧 반영함
    setAnalyzingIds((prev) => { const n = new Set(prev); n.delete(repoId); return n; });
    setAnalysisStatuses((prev) => ({
      ...prev,
      [repoId]: { ...(prev[repoId] ?? {}), status: 'FAILED', error: '취소됨' } as RepoSyncStatus,
    }));
  }

  function toggleSelect(id: number) {
    setSelectedIds((prev) => {
      const n = new Set(prev);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
    setSaveMessage(null);
    setSaveError(null);
  }

  async function handleSaveSelection() {
    setSaving(true); setSaveError(null); setSaveMessage(null);
    // 저장 전에 deselect된 repo 이름 계산
    const deselectedNames = repos
      .filter((r) => r.isSelected && !selectedIds.has(r.id))
      .map((r) => r.repoName);
    try {
      await saveRepositorySelection(Array.from(selectedIds));
      await loadRepos();
      if (deselectedNames.length > 0) {
        setSaveMessage(`포트폴리오에서 제외됨: ${deselectedNames.join(', ')}`);
      } else {
        setSaveMessage('선택이 저장되었습니다.');
      }
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  }

  async function handleSyncCommits(repoId: number) {
    setSyncingIds((prev) => new Set(prev).add(repoId));
    setSyncErrors((prev) => { const n = { ...prev }; delete n[repoId]; return n; });
    try {
      await syncCommits(repoId);
      // 동기화 완료 → hasCommits 낙관적 업데이트 (페이지 새로고침 없이 분석 버튼 활성화)
      setRepos((prev) => prev.map((r) => r.id === repoId ? { ...r, hasCommits: true } : r));
    } catch (err) {
      setSyncErrors((prev) => ({ ...prev, [repoId]: err instanceof Error ? err.message : '동기화 오류' }));
    } finally {
      setSyncingIds((prev) => { const n = new Set(prev); n.delete(repoId); return n; });
    }
  }

  if (loading) return <p className="text-sm text-zinc-400">repository 목록을 불러오는 중...</p>;

  if (loadError) return (
    <div>
      <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</div>
      <button onClick={loadRepos} className="mt-4 text-sm underline text-zinc-500">다시 시도</button>
    </div>
  );

  if (repos.length === 0) return (
    <div>
      <p className="mb-4 text-sm text-zinc-500">연결된 GitHub 계정이 없거나 repository가 없습니다.</p>
      <Link href="/portfolio/github" className="text-sm underline text-zinc-700">GitHub 연결하기 →</Link>
    </div>
  );

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-zinc-500">
          커밋을 수집할 repository를 선택하세요.
          {selectedIds.size > 0 && (
            <span className="ml-1 font-medium text-zinc-800">{selectedIds.size}개 선택됨</span>
          )}
        </p>
        <button
          onClick={handleSaveSelection}
          disabled={saving}
          className="rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {saving ? '저장 중...' : '선택 저장'}
        </button>
      </div>

      {saveMessage && (
        <p className={`mb-4 text-sm ${saveMessage.startsWith('포트폴리오에서 제외됨') ? 'text-amber-700' : 'text-green-700'}`}>
          {saveMessage.startsWith('포트폴리오에서 제외됨') ? '✓ ' : ''}{saveMessage}
        </p>
      )}
      {saveError && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{saveError}</div>
      )}

      <ul className="flex flex-col gap-3">
        {repos.map((repo) => {
          const isSyncing = syncingIds.has(repo.id);
          const isAnalyzing = analyzingIds.has(repo.id);
          const analysisStatus = analysisStatuses[repo.id];
          const isAnalysisActive = isAnalyzing ||
            (analysisStatus?.status === 'PENDING' || analysisStatus?.status === 'IN_PROGRESS');

          return (
            <li key={repo.id} className="rounded border border-zinc-200 px-4 py-3">
              <div className="flex items-start gap-3">
                <input
                  type="checkbox"
                  checked={selectedIds.has(repo.id)}
                  onChange={() => toggleSelect(repo.id)}
                  className="mt-1 h-4 w-4 shrink-0 cursor-pointer accent-zinc-800"
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <a href={repo.htmlUrl} target="_blank" rel="noopener noreferrer"
                      className="text-sm font-medium text-zinc-900 hover:underline truncate">
                      {repo.fullName}
                    </a>
                    <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${VISIBILITY_STYLE[repo.visibility] ?? 'bg-zinc-100 text-zinc-600'}`}>
                      {repo.visibility}
                    </span>
                  </div>
                  {repo.defaultBranch && (
                    <p className="mt-0.5 text-xs text-zinc-400">기본 브랜치: {repo.defaultBranch}</p>
                  )}
                  {syncErrors[repo.id] && <p className="mt-1 text-xs text-red-600">{syncErrors[repo.id]}</p>}
                  {repo.hasCommits && !isSyncing && (
                    <p className="mt-1 text-xs text-green-600">✓ 커밋 동기화됨</p>
                  )}
                  {!repo.hasCommits && selectedIds.has(repo.id) && !isSyncing && (
                    <p className="mt-1 text-xs text-amber-600">커밋 동기화 후 포트폴리오 분석을 시작할 수 있습니다.</p>
                  )}
                  {analyzeErrors[repo.id] && <p className="mt-1 text-xs text-red-600">{analyzeErrors[repo.id]}</p>}
                  {analysisStatus && <AnalysisStatusBadge status={analysisStatus} />}
                </div>
                <div className="flex shrink-0 flex-col gap-1.5">
                  <button
                    onClick={() => handleSyncCommits(repo.id)}
                    disabled={isSyncing || isAnalysisActive || repo.hasCommits || !selectedIds.has(repo.id)}
                    className="rounded border border-zinc-300 px-3 py-1.5 text-xs text-zinc-600 hover:bg-zinc-50 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {isSyncing ? '동기화 중...' : repo.hasCommits ? '동기화됨' : '커밋 동기화'}
                  </button>
                  <button
                    onClick={() => isAnalysisActive ? handleCancelAnalysis(repo.id) : handleAnalyze(repo.id)}
                    disabled={!repo.hasCommits || !selectedIds.has(repo.id)}
                    className={`rounded border px-3 py-1.5 text-xs disabled:opacity-40 disabled:cursor-not-allowed ${
                      isAnalysisActive
                        ? 'border-red-300 bg-red-50 text-red-700 hover:bg-red-100'
                        : 'border-indigo-300 bg-indigo-50 text-indigo-700 hover:bg-indigo-100'
                    }`}
                  >
                    {isAnalysisActive ? '분석 취소' : '포트폴리오 분석'}
                  </button>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

// ══════════════════════════════════════════════════════
// 탭 2 — 기여한 repository
// ══════════════════════════════════════════════════════
function ContributedTab() {
  const [contributions, setContributions] = useState<ContributedRepo[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [yearsOffset, setYearsOffset] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [noMoreData, setNoMoreData] = useState(false);

  // 항목별 추가 버튼 상태
  const [savingId, setSavingId] = useState<number | null>(null);
  const [saveErrors, setSaveErrors] = useState<Record<number, string>>({});

  // URL 직접 추가 폼
  const [urlInput, setUrlInput] = useState('');
  const [urlLoading, setUrlLoading] = useState(false);
  const [urlError, setUrlError] = useState<string | null>(null);
  const [urlSuccess, setUrlSuccess] = useState<string | null>(null);

  // 첫 진입 시 최근 2년 로드
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
          // 중복 제거 후 append
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

  async function handleSave(repo: ContributedRepo) {
    setSavingId(repo.githubRepoId);
    setSaveErrors((prev) => { const n = { ...prev }; delete n[repo.githubRepoId]; return n; });
    try {
      await saveContribution({
        githubRepoId: repo.githubRepoId,
        nameWithOwner: repo.nameWithOwner,
        url: repo.url,
        language: repo.language,
        repoSizeKb: repo.repoSizeKb,
      });
      // 로컬 상태 업데이트: alreadySaved → true
      setContributions((prev) =>
        prev.map((r) => r.githubRepoId === repo.githubRepoId ? { ...r, alreadySaved: true } : r)
      );
    } catch (err) {
      setSaveErrors((prev) => ({
        ...prev,
        [repo.githubRepoId]: err instanceof Error ? err.message : '추가 중 오류가 발생했습니다.',
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
      // 목록에 이미 없는 경우 alreadySaved=true로 표시
      setContributions((prev) => {
        const exists = prev.some((r) => r.githubRepoId === saved.githubRepoId);
        if (exists) {
          return prev.map((r) =>
            r.githubRepoId === saved.githubRepoId ? { ...r, alreadySaved: true } : r
          );
        }
        return prev;
      });
    } catch (err) {
      setUrlError(err instanceof Error ? err.message : '추가 중 오류가 발생했습니다.');
    } finally {
      setUrlLoading(false);
    }
  }

  if (loading) return <p className="text-sm text-zinc-400">기여한 repository를 불러오는 중...</p>;

  if (loadError) return (
    <div>
      <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</div>
      <button onClick={() => handleLoad(0, false)} className="mt-4 text-sm underline text-zinc-500">다시 시도</button>
    </div>
  );

  return (
    <div>
      <p className="mb-4 text-sm text-zinc-500">
        최근 {(yearsOffset + 1) * 2}년간 커밋을 기여한 public repository 목록입니다.
        추가 버튼을 눌러 포트폴리오에 포함시키세요.
      </p>

      {contributions.length === 0 ? (
        <p className="text-sm text-zinc-400 mb-4">이 기간에 기여한 repository가 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-2 mb-4">
          {contributions.map((repo) => {
            const isSaving = savingId === repo.githubRepoId;
            const saveError = saveErrors[repo.githubRepoId];

            return (
              <li key={repo.githubRepoId} className="rounded border border-zinc-200 px-4 py-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <a href={repo.url} target="_blank" rel="noopener noreferrer"
                        className="text-sm font-medium text-zinc-900 hover:underline truncate">
                        {repo.nameWithOwner}
                      </a>
                      {repo.language && (
                        <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500">
                          {repo.language}
                        </span>
                      )}
                    </div>
                    <p className="mt-0.5 text-xs text-zinc-400">
                      기여 커밋 {repo.contributionCount}개
                      {repo.repoSizeKb != null && ` · ${(repo.repoSizeKb / 1024).toFixed(1)} MB`}
                    </p>
                    {saveError && <p className="mt-1 text-xs text-red-600">{saveError}</p>}
                  </div>

                  <button
                    onClick={() => handleSave(repo)}
                    disabled={repo.alreadySaved || isSaving}
                    className={`shrink-0 rounded px-3 py-1.5 text-xs font-medium transition-colors ${
                      repo.alreadySaved
                        ? 'bg-zinc-100 text-zinc-400 cursor-default'
                        : 'border border-zinc-300 text-zinc-600 hover:bg-zinc-50 disabled:opacity-50'
                    }`}
                  >
                    {isSaving ? '추가 중...' : repo.alreadySaved ? '이미 추가됨' : '추가'}
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {/* 이전 기여 더 불러오기 */}
      {!noMoreData ? (
        <button
          onClick={() => handleLoad(yearsOffset + 1, true)}
          disabled={loadingMore}
          className="mb-6 w-full rounded border border-zinc-300 py-2 text-sm text-zinc-600 hover:bg-zinc-50 disabled:opacity-50"
        >
          {loadingMore ? '불러오는 중...' : `이전 기여 더 불러오기 (${(yearsOffset + 1) * 2}~${(yearsOffset + 2) * 2}년 전)`}
        </button>
      ) : (
        <p className="mb-6 text-center text-xs text-zinc-400">더 이상 불러올 기여 이력이 없습니다.</p>
      )}

      {/* URL 직접 추가 */}
      <div className="rounded border border-zinc-200 p-4">
        <p className="mb-2 text-sm font-medium text-zinc-700">repo URL로 직접 추가</p>
        <p className="mb-3 text-xs text-zinc-500">
          목록에 없는 기여 repo가 있다면 URL을 입력하세요.
          본인 커밋이 확인된 경우에만 추가됩니다.
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
            className="rounded bg-zinc-900 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {urlLoading ? '확인 중...' : '추가'}
          </button>
        </form>

        {urlError && (
          <p className="mt-2 text-xs text-red-600">{urlError}</p>
        )}
        {urlSuccess && (
          <p className="mt-2 text-xs text-green-600">{urlSuccess}</p>
        )}
      </div>
    </div>
  );
}

// ── 분석 상태 배지 ──────────────────────────────────────────

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
    return <p className="mt-1 text-xs text-zinc-400">변경 사항이 충분하지 않아 분석을 건너뛰었습니다.</p>;
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
