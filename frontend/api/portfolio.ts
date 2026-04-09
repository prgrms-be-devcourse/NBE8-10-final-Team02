import type { PortfolioReadinessDashboard } from '@/types/portfolio';
import { apiFetch } from '@/lib/api-client';

async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body?.error?.message ?? `오류가 발생했습니다. (${res.status})`;
  } catch {
    return `오류가 발생했습니다. (${res.status})`;
  }
}

export class UnauthenticatedError extends Error {
  constructor() { super('UNAUTHENTICATED'); }
}

export async function dismissAllFailedJobs(): Promise<void> {
  const res = await apiFetch('/api/v1/portfolios/me/readiness/alerts/failed-jobs', {
    method: 'DELETE',
  });
  if (!res.ok) throw new Error(await parseError(res));
}

export async function getPortfolioReadiness(): Promise<PortfolioReadinessDashboard> {
  const res = await apiFetch('/api/v1/portfolios/me/readiness', {
    cache: 'no-store',
  });

  if (res.status === 401) throw new UnauthenticatedError();

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as PortfolioReadinessDashboard;
}
