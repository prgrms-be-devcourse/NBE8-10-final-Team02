'use client';

import { useCallback, useEffect, useState } from 'react';
import { getStreak, getHeatmap } from '@/api/activity';
import type { StreakInfo, ActivityEntry } from '@/types/activity';
import StreakBadge from '@/components/activity/StreakBadge';
import ActivityHeatmap from '@/components/activity/ActivityHeatmap';

export default function ActivityPage() {
  const [streak, setStreak] = useState<StreakInfo | null>(null);
  const [entries, setEntries] = useState<ActivityEntry[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    const [streakData, heatmapData] = await Promise.all([
      getStreak(),
      getHeatmap(180),
    ]);
    setStreak(streakData);
    setEntries(heatmapData);
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <main className="mx-auto max-w-4xl px-4 py-8">
        <p className="text-zinc-400">불러오는 중...</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-4xl px-4 py-8">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-900">활동 기록</h1>
        <StreakBadge streak={streak?.currentStreak ?? 0} />
      </div>

      {/* Stats */}
      <div className="mt-6 grid grid-cols-2 gap-4">
        <div className="rounded-xl border border-zinc-200 bg-white px-4 py-3">
          <p className="text-xs text-zinc-500">현재 연속</p>
          <p className="mt-1 text-2xl font-bold text-zinc-900">
            {streak?.currentStreak ?? 0}
            <span className="text-sm font-normal text-zinc-400">일</span>
          </p>
        </div>
        <div className="rounded-xl border border-zinc-200 bg-white px-4 py-3">
          <p className="text-xs text-zinc-500">최장 연속</p>
          <p className="mt-1 text-2xl font-bold text-zinc-900">
            {streak?.longestStreak ?? 0}
            <span className="text-sm font-normal text-zinc-400">일</span>
          </p>
        </div>
      </div>

      {/* Heatmap */}
      <div className="mt-8 rounded-xl border border-zinc-200 bg-white p-5">
        <h2 className="mb-4 text-sm font-medium text-zinc-700">활동 히트맵</h2>
        <ActivityHeatmap entries={entries} days={180} />
      </div>
    </main>
  );
}
