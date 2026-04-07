import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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
    getQuestionsMock.mockResolvedValue([
      {
        id: 11,
        questionOrder: 1,
        questionText: '지원 동기를 설명해주세요.',
        generatedAnswer: null,
        editedAnswer: null,
        toneOption: 'formal',
        lengthOption: 'medium',
        emphasisPoint: null,
      },
    ]);
  });

  it('상단 CTA를 면접 준비로 이동으로 노출하고 생성 액션은 유지한다', async () => {
    render(<GeneratePage />);

    expect(await screen.findByRole('link', { name: '면접 준비로 이동' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 답변 생성' })).toBeInTheDocument();
    expect(screen.getByText('이 화면에서만 등록된 1개 문항의 답변 생성과 전체 재생성을 진행합니다.')).toBeInTheDocument();
  });
});
