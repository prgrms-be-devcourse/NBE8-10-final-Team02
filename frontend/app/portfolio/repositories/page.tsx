'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { getRepositories, saveRepositorySelection, syncCommits } from '@/api/github';
import type { GithubRepository } from '@/types/github';

// visibility 배지 색상
const VISIBILITY_STYLE: Record<string, string> = {
  public: 'bg-green-100 text-green-700',
  private: 'bg-yellow-100 text-yellow-700',
  internal: 'bg-blue-100 text-blue-700',
};

export default function RepositoriesPage() {
  const [repos, setRepos] = useState<GithubRepository[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 체크박스 선택 상태 (로컬 — 저장 전까지 서버에 반영 안 됨)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  // 선택 저장 상태
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // repo별 커밋 동기화 상태
  const [syncingIds, setSyncingIds] = useState<Set<number>>(new Set());
  const [syncErrors, setSyncErrors] = useState<Record<number, string>>({});
  const [syncedIds, setSyncedIds] = useState<Set<number>>(new Set());

  // ── 목록 조회 ─────────────────────────────────────
  const loadRepos = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const { data } = await getRepositories({ page: 1, size: 100 });
      setRepos(data);
      // 서버에 저장된 선택 상태로 초기화
      setSelectedIds(new Set(data.filter((r) => r.isSelected).map((r) => r.id)));
    } catch (err) {
      const msg = err instanceof Error ? err.message : '목록을 불러오지 못했습니다.';
      setLoadError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRepos();
  }, [loadRepos]);

  // ── 체크박스 토글 ──────────────────────────────────
  function toggleSelect(id: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
    // 선택 변경 시 이전 저장 성공 메시지 초기화
    setSaveSuccess(false);
    setSaveError(null);
  }

  // ── 선택 저장 ──────────────────────────────────────
  async function handleSaveSelection() {
    setSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      await saveRepositorySelection(Array.from(selectedIds));
      setSaveSuccess(true);
      // 서버 상태와 로컬 상태를 다시 동기화
      await loadRepos();
    } catch (err) {
      const msg = err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.';
      setSaveError(msg);
    } finally {
      setSaving(false);
    }
  }

  // ── 커밋 동기화 ────────────────────────────────────
  async function handleSyncCommits(repoId: number) {
    setSyncingIds((prev) => new Set(prev).add(repoId));
    setSyncErrors((prev) => {
      const next = { ...prev };
      delete next[repoId];
      return next;
    });
    setSyncedIds((prev) => {
      const next = new Set(prev);
      next.delete(repoId);
      return next;
    });

    try {
      await syncCommits(repoId);
      setSyncedIds((prev) => new Set(prev).add(repoId));
    } catch (err) {
      const msg = err instanceof Error ? err.message : '동기화 중 오류가 발생했습니다.';
      setSyncErrors((prev) => ({ ...prev, [repoId]: msg }));
    } finally {
      setSyncingIds((prev) => {
        const next = new Set(prev);
        next.delete(repoId);
        return next;
      });
    }
  }

  // ── 렌더링 ────────────────────────────────────────

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <p className="text-sm text-zinc-400">repository 목록을 불러오는 중...</p>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {loadError}
        </div>
        <button
          onClick={loadRepos}
          className="mt-4 text-sm underline text-zinc-500"
        >
          다시 시도
        </button>
      </main>
    );
  }

  // GitHub 연결이 없는 경우 (빈 목록)
  if (repos.length === 0) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <p className="mb-4 text-sm text-zinc-500">
          연결된 GitHub 계정이 없거나 repository가 없습니다.
        </p>
        <Link href="/portfolio/github" className="text-sm underline text-zinc-700">
          GitHub 연결하기 →
        </Link>
      </main>
    );
  }

  const selectedCount = selectedIds.size;

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">repository 선택</h1>
          <p className="mt-0.5 text-sm text-zinc-500">
            커밋을 수집할 repository를 선택하세요.
            {selectedCount > 0 && (
              <span className="ml-1 font-medium text-zinc-800">{selectedCount}개 선택됨</span>
            )}
          </p>
        </div>

        <button
          onClick={handleSaveSelection}
          disabled={saving}
          className="rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {saving ? '저장 중...' : '선택 저장'}
        </button>
      </div>

      {/* 저장 결과 */}
      {saveSuccess && (
        <p className="mb-4 text-sm text-green-700">선택이 저장되었습니다.</p>
      )}
      {saveError && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {saveError}
        </div>
      )}

      {/* Repository 목록 */}
      <ul className="flex flex-col gap-3">
        {repos.map((repo) => {
          const isSyncing = syncingIds.has(repo.id);
          const isSynced = syncedIds.has(repo.id);
          const syncError = syncErrors[repo.id];

          return (
            <li
              key={repo.id}
              className="rounded border border-zinc-200 px-4 py-3"
            >
              <div className="flex items-start gap-3">
                {/* 체크박스 */}
                <input
                  type="checkbox"
                  checked={selectedIds.has(repo.id)}
                  onChange={() => toggleSelect(repo.id)}
                  className="mt-1 h-4 w-4 shrink-0 cursor-pointer accent-zinc-800"
                />

                {/* Repo 정보 */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <a
                      href={repo.htmlUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sm font-medium text-zinc-900 hover:underline truncate"
                    >
                      {repo.fullName}
                    </a>
                    <span
                      className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                        VISIBILITY_STYLE[repo.visibility] ?? 'bg-zinc-100 text-zinc-600'
                      }`}
                    >
                      {repo.visibility}
                    </span>
                  </div>
                  {repo.defaultBranch && (
                    <p className="mt-0.5 text-xs text-zinc-400">
                      기본 브랜치: {repo.defaultBranch}
                    </p>
                  )}

                  {/* 동기화 오류 */}
                  {syncError && (
                    <p className="mt-1 text-xs text-red-600">{syncError}</p>
                  )}
                  {/* 동기화 완료 */}
                  {isSynced && (
                    <p className="mt-1 text-xs text-green-600">커밋 동기화가 시작되었습니다.</p>
                  )}
                </div>

                {/* 커밋 동기화 버튼 — 선택된 repo만 활성화 */}
                <button
                  onClick={() => handleSyncCommits(repo.id)}
                  disabled={isSyncing || !selectedIds.has(repo.id)}
                  title={
                    !selectedIds.has(repo.id)
                      ? '먼저 선택 저장 후 동기화할 수 있습니다.'
                      : '커밋 동기화'
                  }
                  className="shrink-0 rounded border border-zinc-300 px-3 py-1.5 text-xs text-zinc-600 hover:bg-zinc-50 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  {isSyncing ? '동기화 중...' : '커밋 동기화'}
                </button>
              </div>
            </li>
          );
        })}
      </ul>

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