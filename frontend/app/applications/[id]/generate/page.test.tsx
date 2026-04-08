import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import userEvent from '@testing-library/user-event';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { generateAnswers, getApplication, getQuestions } from '@/api/application';
import GeneratePage from './page';

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

vi.mock('@/api/application', async () => {
  const actual = await vi.importActual<typeof import('@/api/application')>('@/api/application');
  return {
    ...actual,
    getApplication: vi.fn(),
    getQuestions: vi.fn(),
    generateAnswers: vi.fn(),
  };
});

const getApplicationMock = vi.mocked(getApplication);
const getQuestionsMock = vi.mocked(getQuestions);
const generateAnswersMock = vi.mocked(generateAnswers);

function buildQuestion(overrides: Partial<Awaited<ReturnType<typeof getQuestions>>[number]> = {}) {
  return {
    id: 11,
    questionOrder: 1,
    questionText: '지원 동기를 설명해주세요.',
    generatedAnswer: null,
    editedAnswer: null,
    toneOption: 'formal',
    lengthOption: 'medium',
    emphasisPoint: null,
    ...overrides,
  };
}

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('GeneratePage', () => {
  beforeEach(() => {
    paramsMock.mockReturnValue({ id: '1' });
    getApplicationMock.mockReset();
    getQuestionsMock.mockReset();
    generateAnswersMock.mockReset();

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
    getQuestionsMock.mockResolvedValue([buildQuestion()]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('상단 CTA를 면접 준비로 이동으로 노출하고 생성 액션은 유지한다', async () => {
    render(<GeneratePage />);

    expect(await screen.findByRole('link', { name: '면접 준비로 이동' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 답변 생성' })).toBeInTheDocument();
    expect(screen.getByText('이 화면에서만 등록된 1개 문항의 답변 생성과 전체 재생성을 진행합니다.')).toBeInTheDocument();
  });

  it('기존 생성 답변이 있으면 재생성 라벨을 노출한다', async () => {
    getQuestionsMock.mockResolvedValueOnce([
      buildQuestion({ generatedAnswer: '기존 생성 답변입니다.' }),
    ]);

    render(<GeneratePage />);

    expect(await screen.findByRole('button', { name: 'AI 답변 재생성' })).toBeInTheDocument();
  });

  it('생성 중에는 버튼을 잠그고 pending 상태 카드를 노출한다', async () => {
    const deferred = createDeferred<Awaited<ReturnType<typeof generateAnswers>>>();
    generateAnswersMock.mockReturnValueOnce(deferred.promise);

    render(<GeneratePage />);

    await screen.findByRole('button', { name: 'AI 답변 생성' });

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: 'AI 답변 생성' }));

    expect(screen.getByRole('button', { name: 'AI 답변 생성 중...' })).toBeDisabled();
    expect(screen.getByRole('status', { name: 'AI 답변 생성 진행 상태' })).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByText('AI가 문항별 답변 초안을 생성하는 중입니다.')).toBeInTheDocument();
    expect(screen.getByText('보통 10~30초 정도 걸립니다. 중복으로 누를 필요 없습니다.')).toBeInTheDocument();
  });

  it('생성이 8초 이상 걸리면 long-wait 안내를 노출한다', async () => {
    const deferred = createDeferred<Awaited<ReturnType<typeof generateAnswers>>>();
    generateAnswersMock.mockReturnValueOnce(deferred.promise);

    render(<GeneratePage />);

    await screen.findByRole('button', { name: 'AI 답변 생성' });

    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: 'AI 답변 생성' }));

    await act(async () => {
      vi.advanceTimersByTime(8000);
    });

    expect(screen.getByText('조금 더 걸리고 있습니다. 곧 결과를 보여드립니다.')).toBeInTheDocument();
  });

  it('생성 완료 후 저장됨 인지와 다음 단계 CTA를 함께 노출한다', async () => {
    getQuestionsMock
      .mockResolvedValueOnce([buildQuestion()])
      .mockResolvedValueOnce([buildQuestion({ generatedAnswer: '생성된 답변' })]);
    generateAnswersMock.mockResolvedValueOnce({
      applicationId: 1,
      generatedCount: 1,
      regenerate: false,
      answers: [
        {
          questionId: 11,
          questionText: '지원 동기를 설명해주세요.',
          generatedAnswer: '생성된 답변',
          toneOption: 'formal',
          lengthOption: 'medium',
        },
      ],
    });

    render(<GeneratePage />);

    await screen.findByRole('button', { name: 'AI 답변 생성' });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'AI 답변 생성' }));

    const successCard = await screen.findByRole('status', { name: 'AI 답변 생성 완료 상태' });
    expect(within(successCard).getByText('1개 문항 생성 완료 · 저장됨')).toBeInTheDocument();
    expect(within(successCard).getByRole('link', { name: '면접 준비로 이동' })).toBeInTheDocument();
  });

  it('생성 실패 시 같은 위치에서 에러 상태를 노출하고 다시 생성할 수 있다', async () => {
    generateAnswersMock.mockRejectedValueOnce(new Error('AI 생성이 일시적으로 실패했습니다.'));

    render(<GeneratePage />);

    await screen.findByRole('button', { name: 'AI 답변 생성' });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'AI 답변 생성' }));

    expect(await screen.findByRole('status', { name: 'AI 답변 생성 오류 상태' })).toBeInTheDocument();
    expect(screen.getByText('AI 생성이 일시적으로 실패했습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 답변 생성' })).toBeEnabled();
  });
});
