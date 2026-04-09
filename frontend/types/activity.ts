export interface StreakInfo {
  currentStreak: number;
  longestStreak: number;
}

export interface ActivityEntry {
  date: string; // "YYYY-MM-DD"
  count: number;
}

export interface ScoreTrendEntry {
  sessionId: number;
  score: number;
  completedAt: string;
}

export interface WeakAreaEntry {
  tagName: string;
  category: string;
  avgScore: number;
  count: number;
}

export interface ActivityStats {
  scoreTrend: ScoreTrendEntry[];
  weakAreas: WeakAreaEntry[];
  feedbackWeakAreas: WeakAreaEntry[];
}
