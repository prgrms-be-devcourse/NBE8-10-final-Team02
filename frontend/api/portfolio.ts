import type { PortfolioReadinessDashboard } from '@/types/portfolio';

const base = () =>
  (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000') +
  '/api/v1/portfolios/me/readiness';

async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body?.error?.message ?? `오류가 발생했습니다. (${res.status})`;
  } catch {
    return `오류가 발생했습니다. (${res.status})`;
  }
}

export async function getPortfolioReadiness(): Promise<PortfolioReadinessDashboard> {
  const res = await fetch(base(), {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as PortfolioReadinessDashboard;
}
