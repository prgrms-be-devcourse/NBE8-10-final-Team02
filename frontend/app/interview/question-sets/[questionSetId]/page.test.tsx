import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  addManualQuestion,
  deleteQuestion,
  getQuestionSetDetail,
  startSession,
} from '@/api/interview';
import QuestionSetDetailPage from './page';

const paramsMock = vi.fn();
const pushMock = vi.fn();

vi.mock('next/navigation', () => ({
  useParams: () => paramsMock(),
  useRouter: () => ({ push: pushMock }),
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
    addManualQuestion: vi.fn(),
    deleteQuestion: vi.fn(),
    getQuestionSetDetail: vi.fn(),
    startSession: vi.fn(),
  };
});

const addManualQuestionMock = vi.mocked(addManualQuestion);
const deleteQuestionMock = vi.mocked(deleteQuestion);
const getQuestionSetDetailMock = vi.mocked(getQuestionSetDetail);
const startSessionMock = vi.mocked(startSession);

function createQuestionSet(questionCount: number) {
  return {
    questionSetId: 21,
    applicationId: 7,
    title: '백엔드 실전 질문 세트',
    questionCount,
    difficultyLevel: 'medium' as const,
    createdAt: '2026-04-07T00:00:00Z',
    questions: Array.from({ length: questionCount }, (_, index) => ({
      id: index + 1,
      questionOrder: index + 1,
      questionType: 'behavioral' as const,
      difficultyLevel: 'medium' as const,
      questionText: `질문 ${index + 1}`,
      parentQuestionId: null,
      sourceApplicationQuestionId: null,
    })),
  };
}

describe('QuestionSetDetailPage', () => {
  beforeEach(() => {
    paramsMock.mockReturnValue({ questionSetId: '21' });
    pushMock.mockReset();
    addManualQuestionMock.mockReset();
    deleteQuestionMock.mockReset();
    getQuestionSetDetailMock.mockReset();
    startSessionMock.mockReset();
  });

  it('모의 면접 시작 버튼을 세트 상태 카드 안에 렌더링한다', async () => {
    getQuestionSetDetailMock.mockResolvedValue(createQuestionSet(5));

    render(<QuestionSetDetailPage />);

    await screen.findByText('백엔드 실전 질문 세트');

    const statusSection = screen.getByText('세트 상태').closest('section');

    expect(screen.getByRole('link', { name: '← 면접 준비로' })).toBeInTheDocument();
    expect(statusSection).not.toBeNull();
    expect(within(statusSection as HTMLElement).getByRole('button', { name: '모의 면접 시작' })).toBeInTheDocument();
  });

  it('질문 수가 3개 미만이면 상태 카드에서 경고와 disabled 시작 버튼을 함께 보여준다', async () => {
    getQuestionSetDetailMock.mockResolvedValue(createQuestionSet(2));

    render(<QuestionSetDetailPage />);

    await screen.findByText('백엔드 실전 질문 세트');

    const statusSection = screen.getByText('세트 상태').closest('section');
    const startButton = within(statusSection as HTMLElement).getByRole('button', { name: '모의 면접 시작' });

    expect(within(statusSection as HTMLElement).getByText('세션 시작은 질문 3개 이상 20개 이하일 때만 가능합니다. 현재 질문 수를 먼저 조정해주세요.')).toBeInTheDocument();
    expect(startButton).toBeDisabled();
  });
});
