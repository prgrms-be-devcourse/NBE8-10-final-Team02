export type AiProviderStatus = 'available' | 'minute_rate_limited' | 'daily_exhausted';

export interface MinuteUsage {
  used: number;
  limit: number;
  percentage: number;
  resetInSeconds: number;
}

export interface DailyUsage {
  used: number;
  limit: number;
  percentage: number;
  resetsAt: string;
}

export interface TokenUsageStat {
  minuteUsed: number;
  minuteLimit: number;
  minutePercentage: number;
  dailyUsed: number;
  dailyLimit: number | null;
  dailyPercentage: number | null;
}

export interface ProviderStatus {
  name: string;
  status: AiProviderStatus;
  minuteUsage: MinuteUsage;
  dailyUsage: DailyUsage;
  tokenUsage: TokenUsageStat;
}

export interface AiStatusResponse {
  available: boolean;
  estimatedWaitSeconds?: number;
  message?: string;
  providers: ProviderStatus[];
}
