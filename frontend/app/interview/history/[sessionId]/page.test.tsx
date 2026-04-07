import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { getSessionDetail, getSessionResult, InterviewApiError } from '@/api/interview';
import type { InterviewResult, InterviewSessionDetail } from '@/types/interview';
import InterviewHistoryDetailPage from './page';

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
    getSessionDetail: vi.fn(),
    getSessionResult: vi.fn(),
  };
});

const getSessionDetailMock = vi.mocked(getSessionDetail);
const getSessionResultMock = vi.mocked(getSessionResult);

function createSessionDetail(overrides?: Partial<InterviewSessionDetail>): InterviewSessionDetail {
  return {
    id: 21,
    questionSetId: 8,
    status: 'completed',
    currentQuestion: null,
    completionFollowupContext: null,
    totalQuestionCount: 5,
    answeredQuestionCount: 5,
    remainingQuestionCount: 0,
    resumeAvailable: false,
    lastActivityAt: '2026-04-07T09:55:00Z',
    startedAt: '2026-04-07T09:30:00Z',
    endedAt: '2026-04-07T10:00:00Z',
    ...overrides,
  };
}

function createResult(overrides?: Partial<InterviewResult>): InterviewResult {
  return {
    sessionId: 21,
    questionSetId: 8,
    status: 'completed',
    totalScore: 83,
    summaryFeedback: '근거 설명이 안정적입니다.',
    answers: [
      {
        answerId: 1,
        questionId: 101,
        questionType: 'project',
        questionText: '가장 어려웠던 장애 대응 경험을 설명해주세요.',
        answerText: '원인 후보를 단계적으로 좁혀가며 장애 전파 범위를 먼저 차단했습니다.',
        score: 83,
        evaluationRationale: '문제 인식과 대응 흐름이 분명합니다.',
        tags: [],
      },
    ],
    startedAt: '2026-04-07T09:30:00Z',
    endedAt: '2026-04-07T10:00:00Z',
    ...overrides,
  };
}

describe('InterviewHistoryDetailPage', () => {
  it('pending 결과를 같은 재확인 카드로 보여주고 재확인 성공 시 리포트로 전환한다', async () => {
    paramsMock.mockReturnValue({ sessionId: '21' });
    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());
    getSessionResultMock.mockRejectedValueOnce(
      new InterviewApiError('면접 결과가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.', {
        code: 'INTERVIEW_RESULT_INCOMPLETE',
        retryable: true,
      }),
    );
    getSessionResultMock.mockResolvedValueOnce(createResult());

    render(<InterviewHistoryDetailPage />);

    expect(await screen.findByText('결과를 준비하고 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '결과 재확인' })).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '결과 재확인' }));

    expect(await screen.findByText('결과 확인 가능')).toBeInTheDocument();
    expect(screen.getByText('질문 단위 리뷰')).toBeInTheDocument();
    expect(screen.queryByText('결과를 준비하고 있습니다.')).not.toBeInTheDocument();
  });
});
