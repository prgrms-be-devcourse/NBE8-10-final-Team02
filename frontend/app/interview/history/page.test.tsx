import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { getSessions } from '@/api/interview';
import type { InterviewSession } from '@/types/interview';
import InterviewHistoryPage from './page';

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

vi.mock('@/api/interview', async () => {
  const actual = await vi.importActual<typeof import('@/api/interview')>('@/api/interview');
  return {
    ...actual,
    getSessions: vi.fn(),
  };
});

const getSessionsMock = vi.mocked(getSessions);

function createSession(overrides?: Partial<InterviewSession>): InterviewSession {
  return {
    id: 1,
    questionSetId: 10,
    status: 'feedback_completed',
    totalScore: 88,
    summaryFeedback: '좋은 구조입니다.',
    startedAt: '2026-04-07T09:00:00Z',
    endedAt: '2026-04-07T09:30:00Z',
    ...overrides,
  };
}

describe('InterviewHistoryPage', () => {
  it('completed 세션은 재확인 후보로, feedback_completed 세션은 완료 리포트로 구분한다', async () => {
    getSessionsMock.mockResolvedValueOnce([
      createSession({
        id: 3,
        status: 'completed',
        totalScore: null,
        summaryFeedback: null,
      }),
      createSession({
        id: 4,
        status: 'feedback_completed',
        totalScore: 91,
        summaryFeedback: '문제 해결 흐름이 명확합니다.',
      }),
    ]);

    render(<InterviewHistoryPage />);

    expect(await screen.findByText('과거 세션 목록')).toBeInTheDocument();
    expect(screen.getByText('결과를 준비하고 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '결과 재확인' })).toHaveAttribute('href', '/interview/history/3');
    expect(screen.getByText('피드백 완료')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '결과 보기' })).toHaveAttribute('href', '/interview/history/4');
  });
});
