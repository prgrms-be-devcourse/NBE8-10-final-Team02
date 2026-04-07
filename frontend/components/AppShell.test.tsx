import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AppShell from './AppShell';

const pathnameMock = vi.fn();

vi.mock('next/navigation', () => ({
  usePathname: () => pathnameMock(),
}));

vi.mock('@/components/PortfolioReadinessWidget', () => ({
  __esModule: true,
  default: () => <div data-testid="readiness-widget">준비현황 위젯</div>,
}));

function renderShell(pathname: string, child: ReactNode = <div>페이지 본문</div>) {
  pathnameMock.mockReturnValue(pathname);
  return render(<AppShell>{child}</AppShell>);
}

describe('AppShell', () => {
  beforeEach(() => {
    pathnameMock.mockReset();
  });

  it.each([
    '/portfolio',
    '/applications/1/generate',
    '/interview/history',
    '/practice',
    '/activity',
  ])('로그인 이후 앱 화면 %s 에서는 readiness widget을 노출한다', (pathname) => {
    const { container } = renderShell(pathname);

    expect(screen.getByTestId('readiness-widget')).toBeInTheDocument();
    expect(screen.getByText('페이지 본문')).toBeInTheDocument();
    expect(container.querySelector('aside')).not.toBeNull();
  });

  it('/portfolio/readiness 에서는 전역 readiness widget을 숨긴다', () => {
    const { container } = renderShell('/portfolio/readiness');

    expect(screen.queryByTestId('readiness-widget')).not.toBeInTheDocument();
    expect(screen.getByText('페이지 본문')).toBeInTheDocument();
    expect(container.querySelector('aside')).toBeNull();
  });

  it.each(['/', '/login'])('%s 에서는 shell과 widget을 모두 숨긴다', (pathname) => {
    const { container } = renderShell(pathname);

    expect(screen.queryByTestId('readiness-widget')).not.toBeInTheDocument();
    expect(screen.getByText('페이지 본문')).toBeInTheDocument();
    expect(container.querySelector('aside')).toBeNull();
  });
});
