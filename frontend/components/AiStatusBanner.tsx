'use client';

import type { AiStatusResponse } from '@/types/aistatus';

interface AiStatusBannerProps {
  aiStatus: AiStatusResponse | null;
  loading: boolean;
  error: string | null;
  countdown: number | null;
}

/**
 * AI 서비스 비가용 상태를 알리는 배너.
 * available=true이면 아무것도 렌더링하지 않습니다.
 *
 * 사용처: 자소서 생성 페이지(SCR-10), 질문 생성 페이지(SCR-11)
 */
export default function AiStatusBanner({ aiStatus, loading, error, countdown }: AiStatusBannerProps) {
  if (loading) {
    return (
      <div className="mb-4 rounded-lg border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-500">
        AI 서비스 상태 확인 중...
      </div>
    );
  }

  if (error) {
    return (
      <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
        AI 상태 확인 실패: {error}
      </div>
    );
  }

  if (!aiStatus || aiStatus.available) return null;

  const allDaily = aiStatus.providers.every((p) => p.status === 'daily_exhausted');

  if (allDaily) {
    return (
      <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
        <p className="text-sm font-medium text-red-800">AI 서비스를 현재 사용할 수 없습니다.</p>
        <p className="mt-0.5 text-xs text-red-700">내일 오전 9시 이후 이용 가능합니다.</p>
      </div>
    );
  }

  return (
    <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
      <p className="text-sm font-medium text-amber-800">AI 서비스가 일시적으로 과부하 상태입니다.</p>
      {countdown !== null ? (
        <p className="mt-0.5 text-xs text-amber-700">
          약 <strong className="font-bold">{countdown}초</strong> 후 자동으로 재확인합니다.
        </p>
      ) : aiStatus.estimatedWaitSeconds ? (
        <p className="mt-0.5 text-xs text-amber-700">
          약 <strong className="font-bold">{aiStatus.estimatedWaitSeconds}초</strong> 후 가능합니다.
        </p>
      ) : null}
    </div>
  );
}
