import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PortfolioReadinessWidget from './PortfolioReadinessWidget';
import { getPortfolioReadiness } from '@/api/portfolio';
import { getSessions } from '@/api/interview';
import { useAiStatus } from '@/hooks/useAiStatus';
import type { PortfolioReadinessDashboard } from '@/types/portfolio';

vi.mock('next/navigation', () => ({
  usePathname: () => '/portfolio',
}));

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
  UnauthenticatedError: class UnauthenticatedError extends Error {},
  getPortfolioReadiness: vi.fn(),
}));

vi.mock('@/api/interview', () => ({
  getSessions: vi.fn(),
}));

vi.mock('@/hooks/useAiStatus', () => ({
  useAiStatus: vi.fn(),
}));

const getPortfolioReadinessMock = vi.mocked(getPortfolioReadiness);
const getSessionsMock = vi.mocked(getSessions);
const useAiStatusMock = vi.mocked(useAiStatus);

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

describe('PortfolioReadinessWidget', () => {
  beforeEach(() => {
    getPortfolioReadinessMock.mockReset();
    getSessionsMock.mockReset();
    useAiStatusMock.mockReset();

    useAiStatusMock.mockReturnValue({
      aiStatus: {
        available: true,
        message: '정상',
        providers: [],
      },
      loading: false,
      error: null,
      countdown: null,
    });
  });

  it('promotes an active paused session above the application CTA', async () => {
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

    render(<PortfolioReadinessWidget />);

    expect(await screen.findByRole('link', { name: '세션 재개' })).toHaveAttribute(
      'href',
      '/interview/sessions/901',
    );
    expect(screen.getByText('일시정지된 모의 면접 세션이 있어 먼저 재개할 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '지원 준비로 이동' })).toHaveAttribute(
      'href',
      '/applications',
    );
  });

  it('disables the application CTA when AI is unavailable', async () => {
    getPortfolioReadinessMock.mockResolvedValue(createDashboard());
    getSessionsMock.mockResolvedValue([]);
    useAiStatusMock.mockReturnValue({
      aiStatus: {
        available: false,
        message: '일시 제한',
        providers: [],
      },
      loading: false,
      error: null,
      countdown: 18,
    });

    render(<PortfolioReadinessWidget />);

    const disabledLink = await screen.findByRole('link', { name: 'AI 기능 대기 중...' });
    expect(disabledLink).toHaveAttribute('href', '/applications');
    expect(disabledLink).toHaveAttribute('aria-disabled', 'true');
    expect(disabledLink.className).toContain('pointer-events-none');
  });

  it('keeps the session CTA enabled even when AI is unavailable', async () => {
    getPortfolioReadinessMock.mockResolvedValue(createDashboard());
    getSessionsMock.mockResolvedValue([
      {
        id: 902,
        questionSetId: 702,
        status: 'in_progress',
        totalScore: null,
        summaryFeedback: null,
        startedAt: '2026-04-07T10:00:00Z',
        endedAt: null,
      },
    ]);
    useAiStatusMock.mockReturnValue({
      aiStatus: {
        available: false,
        message: '일시 제한',
        providers: [],
      },
      loading: false,
      error: null,
      countdown: 12,
    });

    render(<PortfolioReadinessWidget />);

    const activeSessionLink = await screen.findByRole('link', { name: '세션 이어서 진행' });
    expect(activeSessionLink).toHaveAttribute('href', '/interview/sessions/902');
    expect(activeSessionLink).toHaveAttribute('aria-disabled', 'false');
    expect(activeSessionLink.className).not.toContain('pointer-events-none');
  });
});
