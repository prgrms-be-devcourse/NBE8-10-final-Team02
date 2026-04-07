import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { getSessionResult, InterviewApiError } from '@/api/interview';
import InterviewSessionResultPage from './page';

const paramsMock = vi.fn();

vi.mock('next/navigation', () => ({
  useParams: () => paramsMock(),
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

vi.mock('@/api/interview', async () => {
  const actual = await vi.importActual<typeof import('@/api/interview')>('@/api/interview');
  return {
    ...actual,
    getSessionResult: vi.fn(),
  };
});

const getSessionResultMock = vi.mocked(getSessionResult);

describe('InterviewSessionResultPage', () => {
  it('INTERVIEW_RESULT_INCOMPLETE를 정보형 재확인 카드로 보여준다', async () => {
    paramsMock.mockReturnValue({ sessionId: '12' });
    getSessionResultMock.mockRejectedValueOnce(
      new InterviewApiError('면접 결과가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.', {
        code: 'INTERVIEW_RESULT_INCOMPLETE',
        retryable: true,
      }),
    );

    render(<InterviewSessionResultPage />);

    expect(await screen.findByText('결과를 준비하고 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '결과 재확인' })).toBeInTheDocument();
    expect(screen.getByText('면접 결과가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다시 보기' })).not.toBeInTheDocument();
  });
});
