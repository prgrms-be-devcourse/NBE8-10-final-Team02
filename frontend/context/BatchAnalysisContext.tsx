'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { useRouter } from 'next/navigation';
import { analyzeBatch, cancelBatch, getAnalysisStatus } from '@/api/github';
import type { RepoSyncStatus } from '@/types/github';

// ── 타입 ──────────────────────────────────────────────────────────────

interface ActiveBatch {
  repoIds: number[];
  repoNames: Record<number, string>;  // id → fullName
  statuses: Record<number, RepoSyncStatus | null>;
}

interface BatchAnalysisContextValue {
  activeBatch: ActiveBatch | null;
  preSelectedIds: number[];
  /** cacheInvalidateKey가 증가하면 구독자(readiness 등)가 리페치해야 한다 */
  cacheInvalidateKey: number;
  startBatch: (repoIds: number[], repoNames: Record<number, string>) => Promise<void>;
  handleCancelBatch: () => Promise<void>;
  handleRetryFailed: () => void;
  clearPreSelected: () => void;
  dismissWidget: () => void;
}

// ── 상수 ─────────────────────────────────────────────────────────────

const STORAGE_KEY = 'batch_analysis_state';
const POLL_INTERVAL = 3000;

// ── Context ───────────────────────────────────────────────────────────

const BatchAnalysisContext = createContext<BatchAnalysisContextValue | null>(null);

// ── Provider ─────────────────────────────────────────────────────────

export function BatchAnalysisProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [activeBatch, setActiveBatch] = useState<ActiveBatch | null>(null);
  const [preSelectedIds, setPreSelectedIds] = useState<number[]>([]);
  const [cacheInvalidateKey, setCacheInvalidateKey] = useState(0);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── sessionStorage 직렬화/복원 ──

  function saveToStorage(batch: ActiveBatch | null) {
    if (batch) {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(batch));
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }

  function loadFromStorage(): ActiveBatch | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as ActiveBatch;
      // repoNames 필드가 없는 구형 데이터 대응
      if (!parsed.repoNames) parsed.repoNames = {};
      return parsed;
    } catch {
      return null;
    }
  }

  // ── 폴링 ──

  const poll = useCallback(async (batch: ActiveBatch) => {
    const updates: Record<number, RepoSyncStatus | null> = {};
    await Promise.all(
      batch.repoIds.map(async (id) => {
        try {
          updates[id] = await getAnalysisStatus(id);
        } catch {
          // 개별 폴링 실패는 무시
        }
      })
    );

    setActiveBatch((prev) => {
      if (!prev) return null;
      const next = { ...prev, statuses: { ...prev.statuses, ...updates } };
      saveToStorage(next);

      // 전원 종료 상태이면 폴링 중단
      const allDone = next.repoIds.every((id) => {
        const s = next.statuses[id];
        return s?.status === 'COMPLETED' || s?.status === 'FAILED' || s?.status === 'SKIPPED';
      });

      if (allDone) {
        stopPoll();
        const allSuccess = next.repoIds.every((id) => {
          const s = next.statuses[id];
          return s?.status === 'COMPLETED' || s?.status === 'SKIPPED';
        });
        if (allSuccess) {
          // 완료 → 캐시 무효화 후 3초 뒤 위젯 자동 소멸
          setCacheInvalidateKey((k) => k + 1);
          setTimeout(() => {
            setActiveBatch(null);
            saveToStorage(null);
          }, 3000);
        }
      }

      return next;
    });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function startPoll(batch: ActiveBatch) {
    stopPoll();
    pollRef.current = setInterval(() => poll(batch), POLL_INTERVAL);
  }

  function stopPoll() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  // 폴링은 activeBatch 변경마다 재설정
  useEffect(() => {
    if (!activeBatch) return;
    const allDone = activeBatch.repoIds.every((id) => {
      const s = activeBatch.statuses[id];
      return s?.status === 'COMPLETED' || s?.status === 'FAILED' || s?.status === 'SKIPPED';
    });
    if (allDone) return;
    startPoll(activeBatch);
    return stopPoll;
  }, [activeBatch]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── 새로고침 복구 ──

  useEffect(() => {
    const stored = loadFromStorage();
    if (!stored) return;
    // 이미 전원 종료된 배치라면 복원 불필요
    const allDone = stored.repoIds.every((id) => {
      const s = stored.statuses[id];
      return s?.status === 'COMPLETED' || s?.status === 'FAILED' || s?.status === 'SKIPPED';
    });
    if (!allDone) {
      setActiveBatch(stored);
    } else {
      saveToStorage(null);
    }
  }, []);

  // ── 공개 API ──

  const startBatch = useCallback(async (repoIds: number[], repoNames: Record<number, string>) => {
    await analyzeBatch(repoIds);
    const initialStatuses: Record<number, RepoSyncStatus | null> = {};
    repoIds.forEach((id) => { initialStatuses[id] = null; });
    const batch: ActiveBatch = { repoIds, repoNames, statuses: initialStatuses };
    setActiveBatch(batch);
    saveToStorage(batch);
  }, []);

  const handleCancelBatch = useCallback(async () => {
    if (!activeBatch) return;
    try {
      await cancelBatch(activeBatch.repoIds);
    } catch { /* 취소 실패는 무시 */ }
    stopPoll();
    setActiveBatch(null);
    saveToStorage(null);
  }, [activeBatch]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleRetryFailed = useCallback(() => {
    if (!activeBatch) return;
    const failed = activeBatch.repoIds.filter((id) => {
      const s = activeBatch.statuses[id];
      return s?.status === 'FAILED';
    });
    stopPoll();
    setActiveBatch(null);
    saveToStorage(null);
    setPreSelectedIds(failed);
    router.push('/portfolio/repositories');
  }, [activeBatch, router]); // eslint-disable-line react-hooks/exhaustive-deps

  const clearPreSelected = useCallback(() => {
    setPreSelectedIds([]);
  }, []);

  const dismissWidget = useCallback(() => {
    stopPoll();
    setActiveBatch(null);
    saveToStorage(null);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <BatchAnalysisContext.Provider
      value={{
        activeBatch,
        preSelectedIds,
        cacheInvalidateKey,
        startBatch,
        handleCancelBatch,
        handleRetryFailed,
        clearPreSelected,
        dismissWidget,
      }}
    >
      {children}
    </BatchAnalysisContext.Provider>
  );
}

// ── Hook ──────────────────────────────────────────────────────────────

export function useBatchAnalysis(): BatchAnalysisContextValue {
  const ctx = useContext(BatchAnalysisContext);
  if (!ctx) throw new Error('useBatchAnalysis must be used within BatchAnalysisProvider');
  return ctx;
}
