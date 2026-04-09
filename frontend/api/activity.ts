import type { ActivityEntry, ActivityStats, StreakInfo } from '@/types/activity';

const apiBase = () => process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000';

export async function getStreak(): Promise<StreakInfo> {
  try {
    const res = await fetch(`${apiBase()}/api/v1/activity/streak`, {
      credentials: 'include',
      cache: 'no-store',
    });
    if (!res.ok) return { currentStreak: 0, longestStreak: 0 };
    const body = await res.json();
    return body.data as StreakInfo;
  } catch {
    return { currentStreak: 0, longestStreak: 0 };
  }
}

export async function getHeatmap(days = 180): Promise<ActivityEntry[]> {
  try {
    const res = await fetch(`${apiBase()}/api/v1/activity/heatmap?days=${days}`, {
      credentials: 'include',
      cache: 'no-store',
    });
    if (!res.ok) return [];
    const body = await res.json();
    return body.data as ActivityEntry[];
  } catch {
    return [];
  }
}

export async function getStats(): Promise<ActivityStats> {
  try {
    const res = await fetch(`${apiBase()}/api/v1/activity/stats`, {
      credentials: 'include',
      cache: 'no-store',
    });
    if (!res.ok) return { scoreTrend: [], weakAreas: [], feedbackWeakAreas: [] };
    const body = await res.json();
    return body.data as ActivityStats;
  } catch {
    return { scoreTrend: [], weakAreas: [], feedbackWeakAreas: [] };
  }
}
