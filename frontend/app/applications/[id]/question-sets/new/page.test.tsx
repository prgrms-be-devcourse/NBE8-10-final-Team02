import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getApplication } from '@/api/application';
import { createQuestionSet, getQuestionSets } from '@/api/interview';
import NewQuestionSetPage from './page';

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

vi.mock('@/api/application', async () => {
  const actual = await vi.importActual<typeof import('@/api/application')>('@/api/application');
  return {
    ...actual,
    getApplication: vi.fn(),
  };
});

vi.mock('@/api/interview', async () => {
  const actual = await vi.importActual<typeof import('@/api/interview')>('@/api/interview');
  return {
    ...actual,
    createQuestionSet: vi.fn(),
    getQuestionSets: vi.fn(),
  };
});

const getApplicationMock = vi.mocked(getApplication);
const createQuestionSetMock = vi.mocked(createQuestionSet);
const getQuestionSetsMock = vi.mocked(getQuestionSets);

describe('NewQuestionSetPage', () => {
  beforeEach(() => {
    paramsMock.mockReturnValue({ id: '1' });
    pushMock.mockReset();
    getApplicationMock.mockReset();
    createQuestionSetMock.mockReset();
    getQuestionSetsMock.mockReset();

    getApplicationMock.mockResolvedValue({
      id: 1,
      applicationTitle: '2026 상반기 공채',
      companyName: '네이버',
      jobRole: '백엔드 개발자',
      status: 'ready',
      createdAt: '2026-04-07T00:00:00Z',
      updatedAt: '2026-04-07T00:00:00Z',
      applicationType: null,
    });
    getQuestionSetsMock.mockResolvedValue([
      {
        questionSetId: 11,
        applicationId: 1,
        title: '먼저 만든 세트',
        questionCount: 4,
        difficultyLevel: 'easy',
        createdAt: '2026-04-06T00:00:00Z',
      },
      {
        questionSetId: 12,
        applicationId: 1,
        title: '가장 최근 세트',
        questionCount: 6,
        difficultyLevel: 'medium',
        createdAt: '2026-04-07T00:00:00Z',
      },
    ]);
  });

  it('면접 준비 흐름의 제목과 생성 버튼 라벨을 노출한다', async () => {
    render(<NewQuestionSetPage />);

    expect(await screen.findByRole('heading', { name: '면접 준비' })).toBeInTheDocument();
    expect(screen.getByText('네이버 · 백엔드 개발자 기준으로 질문 세트를 만들고 기존 세트를 다시 열 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '질문 세트 만들기' })).toBeInTheDocument();
  });

  it('기존 질문 세트는 최신순으로 보이고 카드 본문 클릭과 내부 CTA가 상세 이동으로 연결된다', async () => {
    render(<NewQuestionSetPage />);

    await screen.findByText('가장 최근 세트');

    const cards = screen.getAllByRole('link');
    const setCards = cards.filter((card) =>
      within(card).queryByText(/질문 \d+개/) !== null,
    );

    expect(within(setCards[0]).getByText('가장 최근 세트')).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(within(setCards[0]).getByRole('button', { name: '세트 열기' }));
    expect(pushMock).toHaveBeenCalledTimes(1);
    expect(pushMock).toHaveBeenNthCalledWith(1, '/interview/question-sets/12');

    await user.click(within(setCards[1]).getByText('먼저 만든 세트'));
    expect(pushMock).toHaveBeenCalledTimes(2);
    expect(pushMock).toHaveBeenNthCalledWith(2, '/interview/question-sets/11');
  });
});
