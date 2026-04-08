import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PortfolioReadinessPage from './page';
import { getPortfolioReadiness } from '@/api/portfolio';
import { getRepositories } from '@/api/github';
import { getSessions } from '@/api/interview';
import type { PortfolioReadinessDashboard } from '@/types/portfolio';

vi.mock('next/link', () => ({
  default: ({
    children,
    href,
    ...props
  }: ComponentPropsWithoutRef<'a'> & { children?: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock('@/api/portfolio', () => ({
  getPortfolioReadiness: vi.fn(),
}));

vi.mock('@/api/github', () => ({
  getRepositories: vi.fn(),
}));

vi.mock('@/api/interview', () => ({
  getSessions: vi.fn(),
}));

vi.mock('@/context/BatchAnalysisContext', () => ({
  useBatchAnalysis: () => ({
    cacheInvalidateKey: 0,
  }),
}));

vi.mock('@/components/RepoSummaryModal', () => ({
  default: () => null,
}));

const getPortfolioReadinessMock = vi.mocked(getPortfolioReadiness);
const getRepositoriesMock = vi.mocked(getRepositories);
const getSessionsMock = vi.mocked(getSessions);

function createDashboard(
  overrides?: Partial<PortfolioReadinessDashboard>,
): PortfolioReadinessDashboard {
  return {
    profile: {
      userId: 1,
      displayName: '테스터',
      email: 'tester@example.com',
      profileImageUrl: null,
    },
    github: {
      connectionStatus: 'connected',
      scopeStatus: 'public_only',
      selectedRepositoryCount: 2,
      recentCollectedCommitCount: {
        status: 'not_ready',
        value: null,
      },
    },
    documents: {
      totalCount: 2,
      extractSuccessCount: 2,
      extractFailedCount: 0,
    },
    readiness: {
      missingItems: [],
      nextRecommendedAction: 'start_application',
      canStartApplication: true,
    },
    alerts: {
      recentFailedJobs: {
        status: 'not_ready',
        items: null,
      },
    },
    ...overrides,
  };
}

describe('PortfolioReadinessPage', () => {
  beforeEach(() => {
    getPortfolioReadinessMock.mockReset();
    getRepositoriesMock.mockReset();
    getSessionsMock.mockReset();

    getRepositoriesMock.mockResolvedValue({
      data: [],
      pagination: {
        page: 1,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
    });
  });

  it('shows an active-session primary CTA and keeps applications as secondary action', async () => {
    getPortfolioReadinessMock.mockResolvedValue(createDashboard());
    getSessionsMock.mockResolvedValue([
      {
        id: 901,
        questionSetId: 701,
        status: 'paused',
        totalScore: null,
        summaryFeedback: null,
        startedAt: '2026-04-07T10:00:00Z',
        endedAt: null,
      },
    ]);

    render(<PortfolioReadinessPage />);

    expect(await screen.findByRole('heading', { name: '테스터님의 준비 현황' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: '세션 재개' })[0]).toHaveAttribute(
      'href',
      '/interview/sessions/901',
    );
    expect(screen.getAllByRole('link', { name: '지원 준비 시작' })[0]).toHaveAttribute(
      'href',
      '/applications',
    );
    expect(screen.getByText('지원 준비는 가능하며, 먼저 복귀할 면접 세션이 있습니다.')).toBeInTheDocument();
  });

  it('falls back to the readiness CTA when the session lookup fails', async () => {
    getPortfolioReadinessMock.mockResolvedValue(createDashboard());
    getSessionsMock.mockRejectedValue(new Error('session lookup failed'));

    render(<PortfolioReadinessPage />);

    const readinessLinks = await screen.findAllByRole('link', { name: '지원 준비 시작' });
    expect(readinessLinks).toHaveLength(2);
    readinessLinks.forEach((link) => {
      expect(link).toHaveAttribute('href', '/applications');
    });
    expect(screen.queryByRole('link', { name: '세션 재개' })).not.toBeInTheDocument();
  });
});
