'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { getAiStatus } from '@/api/aistatus';
import type { AiStatusResponse } from '@/types/aistatus';

interface UseAiStatusOptions {
  /** 폴링 주기 (ms). 기본값 10초. daily_exhausted 상태에서는 폴링이 자동 중단됩니다. */
  pollIntervalMs?: number;
}

interface UseAiStatusResult {
  aiStatus: AiStatusResponse | null;
  loading: boolean;
  error: string | null;
  /** 분당 초과 상태일 때 남은 대기 초 카운트다운. null이면 비표시. */
  countdown: number | null;
}

export function useAiStatus({ pollIntervalMs = 10000 }: UseAiStatusOptions = {}): UseAiStatusResult {
  const [aiStatus, setAiStatus] = useState<AiStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [countdown, setCountdown] = useState<number | null>(null);

  // 카운트다운 타이머 ref (cleanup 용도)
  const countdownTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // daily_exhausted 시 폴링 중단 여부
  const isDailyExhaustedRef = useRef(false);
  // fetchStatus 최신 참조 (카운트다운 완료 후 재조회에 사용)
  const fetchStatusRef = useRef<() => void>(() => undefined);

  const clearCountdownTimer = useCallback(() => {
    if (countdownTimerRef.current !== null) {
      clearInterval(countdownTimerRef.current);
      countdownTimerRef.current = null;
    }
    setCountdown(null);
  }, []);

  const fetchStatus = useCallback(async () => {
    try {
      setError(null);
      const data = await getAiStatus();
      setAiStatus(data);

      const allDaily = data.providers.every((p) => p.status === 'daily_exhausted');
      isDailyExhaustedRef.current = allDaily;

      clearCountdownTimer();

      // 분당 초과 상태: 카운트다운 시작
      if (!data.available && !allDaily && data.estimatedWaitSeconds) {
        let remaining = data.estimatedWaitSeconds;
        setCountdown(remaining);
        countdownTimerRef.current = setInterval(() => {
          remaining -= 1;
          if (remaining <= 0) {
            clearInterval(countdownTimerRef.current!);
            countdownTimerRef.current = null;
            setCountdown(null);
            // 카운트다운 0 도달 → 즉시 재조회
            fetchStatusRef.current();
          } else {
            setCountdown(remaining);
          }
        }, 1000);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 상태를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [clearCountdownTimer]);

  // fetchStatusRef를 항상 최신으로 유지
  useEffect(() => {
    fetchStatusRef.current = () => { void fetchStatus(); };
  }, [fetchStatus]);

  useEffect(() => {
    void fetchStatus();

    const pollId = setInterval(() => {
      // daily_exhausted이면 추가 폴링 불필요
      if (!isDailyExhaustedRef.current) {
        void fetchStatus();
      }
    }, pollIntervalMs);

    return () => {
      clearInterval(pollId);
      clearCountdownTimer();
    };
  }, [fetchStatus, pollIntervalMs, clearCountdownTimer]);

  return { aiStatus, loading, error, countdown };
}
