import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getSessionResult, InterviewApiError } from '@/api/interview';
import type { InterviewResult } from '@/types/interview';
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

vi.mock('@/components/InterviewResultReport', () => ({
  default: ({ result }: { result: InterviewResult }) => (
    <section>
      <h1>mock report</h1>
      <p>{result.summaryFeedback}</p>
    </section>
  ),
}));

const getSessionResultMock = vi.mocked(getSessionResult);

async function flushPromises() {
  await act(async () => {
    await Promise.resolve();
  });
}

function createIncompleteError() {
  return new InterviewApiError('면접 결과가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.', {
    code: 'INTERVIEW_RESULT_INCOMPLETE',
    retryable: true,
  });
}

function createResult(): InterviewResult {
  return {
    sessionId: 12,
    questionSetId: 5,
    status: 'feedback_completed',
    totalScore: 84,
    summaryFeedback: '종합 피드백이 준비되었습니다.',
    answers: [],
    startedAt: '2026-04-08T01:00:00Z',
    endedAt: '2026-04-08T01:30:00Z',
  };
}

describe('InterviewSessionResultPage', () => {
  beforeEach(() => {
    paramsMock.mockReturnValue({ sessionId: '12' });
    getSessionResultMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('INTERVIEW_RESULT_INCOMPLETE를 자동 재확인 상태가 있는 pending card로 보여준다', async () => {
    vi.useFakeTimers();
    getSessionResultMock.mockRejectedValueOnce(createIncompleteError());

    render(<InterviewSessionResultPage />);
    await flushPromises();

    expect(screen.getByText('결과를 준비하고 있습니다.')).toBeInTheDocument();
    expect(screen.getByText('자동으로 다시 확인 중 1/3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '결과 재확인' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다시 보기' })).not.toBeInTheDocument();
  });

  it('자동 재확인 도중 결과가 준비되면 결과 리포트로 전환한다', async () => {
    vi.useFakeTimers();
    getSessionResultMock
      .mockRejectedValueOnce(createIncompleteError())
      .mockResolvedValueOnce(createResult());

    render(<InterviewSessionResultPage />);
    await flushPromises();
    expect(screen.getByText('결과를 준비하고 있습니다.')).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(4_000);
    });
    await flushPromises();

    expect(screen.getByText('mock report')).toBeInTheDocument();
    expect(screen.getByText('종합 피드백이 준비되었습니다.')).toBeInTheDocument();
    expect(screen.queryByText('결과를 준비하고 있습니다.')).not.toBeInTheDocument();
    expect(getSessionResultMock).toHaveBeenCalledTimes(2);
  });

  it('3회 자동 재확인 후에도 pending이면 수동 재확인 상태로 멈춘다', async () => {
    vi.useFakeTimers();
    getSessionResultMock
      .mockRejectedValueOnce(createIncompleteError())
      .mockRejectedValueOnce(createIncompleteError())
      .mockRejectedValueOnce(createIncompleteError())
      .mockRejectedValueOnce(createIncompleteError());

    render(<InterviewSessionResultPage />);
    await flushPromises();
    expect(screen.getByText('결과를 준비하고 있습니다.')).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(12_000);
    });
    await flushPromises();

    expect(screen.getByText('결과 재확인 가능')).toBeInTheDocument();
    expect(screen.queryByText('자동으로 다시 확인 중 3/3')).not.toBeInTheDocument();
    expect(getSessionResultMock).toHaveBeenCalledTimes(4);
  });

  it('수동 결과 재확인은 한 번만 조회하고 자동 재확인을 다시 시작하지 않는다', async () => {
    vi.useFakeTimers();
    getSessionResultMock
      .mockRejectedValueOnce(createIncompleteError())
      .mockRejectedValueOnce(createIncompleteError())
      .mockRejectedValueOnce(createIncompleteError())
      .mockRejectedValueOnce(createIncompleteError())
      .mockRejectedValueOnce(createIncompleteError());

    render(<InterviewSessionResultPage />);
    await flushPromises();
    expect(screen.getByText('결과를 준비하고 있습니다.')).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(12_000);
    });

    screen.getByRole('button', { name: '결과 재확인' }).click();
    await flushPromises();

    expect(getSessionResultMock).toHaveBeenCalledTimes(5);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(8_000);
    });
    await flushPromises();

    expect(getSessionResultMock).toHaveBeenCalledTimes(5);
    expect(screen.getByText('결과 재확인 가능')).toBeInTheDocument();
  });

  it('일반 오류는 다시 보기 에러 상태를 유지한다', async () => {
    getSessionResultMock.mockRejectedValueOnce(new Error('면접 결과를 불러오지 못했습니다.'));

    render(<InterviewSessionResultPage />);

    expect(await screen.findByText('면접 결과를 불러오지 못했습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 보기' })).toBeInTheDocument();
  });
});
