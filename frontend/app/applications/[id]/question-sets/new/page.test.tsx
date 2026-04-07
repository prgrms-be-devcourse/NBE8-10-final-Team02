import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

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

  afterEach(() => {
    vi.useRealTimers();
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

  it('생성 중에는 버튼과 질문 수 입력을 잠그고 pending 상태 카드를 노출한다', async () => {
    const deferred = createDeferred<Awaited<ReturnType<typeof createQuestionSet>>>();
    createQuestionSetMock.mockReturnValueOnce(deferred.promise);

    render(<NewQuestionSetPage />);

    await screen.findByRole('button', { name: '질문 세트 만들기' });

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '질문 세트 만들기' }));

    expect(screen.getByRole('button', { name: '질문 세트 만드는 중...' })).toBeDisabled();
    expect(screen.getByRole('slider')).toBeDisabled();
    expect(screen.getByRole('status', { name: 'AI 질문 생성 진행 상태' })).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByText('AI가 질문 세트를 구성하는 중입니다.')).toBeInTheDocument();
    expect(screen.getByText('질문 수와 유형에 따라 최대 30초 정도 걸릴 수 있습니다.')).toBeInTheDocument();
  });

  it('생성이 8초 이상 걸리면 long-wait 안내를 노출한다', async () => {
    const deferred = createDeferred<Awaited<ReturnType<typeof createQuestionSet>>>();
    createQuestionSetMock.mockReturnValueOnce(deferred.promise);

    render(<NewQuestionSetPage />);

    await screen.findByRole('button', { name: '질문 세트 만들기' });

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: '질문 세트 만들기' }));

    await act(async () => {
      vi.advanceTimersByTime(8000);
    });

    expect(screen.getByText('조금 더 걸리고 있습니다. 곧 결과를 보여드립니다.')).toBeInTheDocument();
  });

  it('생성 실패 시 현재 화면에서 에러 상태와 재시도 가능 상태를 유지한다', async () => {
    createQuestionSetMock.mockRejectedValueOnce(new Error('질문 생성에 실패했습니다.'));

    render(<NewQuestionSetPage />);

    await screen.findByRole('button', { name: '질문 세트 만들기' });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '질문 세트 만들기' }));

    expect(await screen.findByRole('status', { name: 'AI 질문 생성 오류 상태' })).toBeInTheDocument();
    expect(screen.getByText('질문 생성에 실패했습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '질문 세트 만들기' })).toBeEnabled();
  });

  it('생성 성공 시 질문 세트 상세 화면으로 이동한다', async () => {
    createQuestionSetMock.mockResolvedValueOnce({
      questionSetId: 25,
      applicationId: 1,
      title: '새 질문 세트',
      questionCount: 5,
      difficultyLevel: 'medium',
      createdAt: '2026-04-07T00:00:00Z',
    });

    render(<NewQuestionSetPage />);

    await screen.findByRole('button', { name: '질문 세트 만들기' });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '질문 세트 만들기' }));

    expect(pushMock).toHaveBeenCalledWith('/interview/question-sets/25');
  });
});
