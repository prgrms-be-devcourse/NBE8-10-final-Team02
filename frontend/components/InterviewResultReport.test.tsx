import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { InterviewResult } from '@/types/interview';
import InterviewResultReport from './InterviewResultReport';

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

function createResult(overrides?: Partial<InterviewResult>): InterviewResult {
  return {
    sessionId: 55,
    questionSetId: 9,
    status: 'completed',
    totalScore: 84,
    summaryFeedback: '경험 근거를 조금 더 구체화하면 좋습니다.',
    answers: [
      {
        answerId: 1,
        questionId: 11,
        questionType: 'project',
        questionText: '장애 대응 경험을 설명해주세요.',
        answerText: '장애 범위를 먼저 줄이고 로그 패턴을 단계적으로 비교했습니다.',
        score: 84,
        evaluationRationale: '대응 흐름이 비교적 명확합니다.',
        tags: [],
      },
    ],
    startedAt: '2026-04-07T09:00:00Z',
    endedAt: '2026-04-07T09:30:00Z',
    ...overrides,
  };
}

describe('InterviewResultReport', () => {
  it('completed 결과 payload를 pending이 아닌 사용 가능한 결과 상태로 렌더링한다', () => {
    render(
      <InterviewResultReport
        result={createResult()}
        backHref="/interview/history"
        backLabel="면접 히스토리로 돌아가기"
      />,
    );

    expect(screen.getByText('결과 확인 가능')).toBeInTheDocument();
    expect(screen.queryByText('결과 준비 중')).not.toBeInTheDocument();
  });
});
