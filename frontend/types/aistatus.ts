
export type AiProviderStatus = 'AVAILABLE' | 'MINUTE_RATE_LIMITED' | 'DAILY_EXHAUSTED';

export interface ProviderStatus {
  provider: string; // 예: 'Google Gemini', 'OpenAI' 등
  status: AiProviderStatus;
}

export interface AiStatusResponse {
  available: boolean;
  estimatedWaitSeconds?: number;
  message?: string;
  providers: ProviderStatus[];
}


