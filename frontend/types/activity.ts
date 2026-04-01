export interface StreakInfo {
  currentStreak: number;
  longestStreak: number;
}

export interface ActivityEntry {
  date: string; // "YYYY-MM-DD"
  count: number;
}
