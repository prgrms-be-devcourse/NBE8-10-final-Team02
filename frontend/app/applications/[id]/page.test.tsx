import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getApplication, getQuestions, saveQuestions, saveSources } from '@/api/application';
import { getDocuments } from '@/api/document';
import { getRepositories } from '@/api/github';
import ApplicationDetailPage from './page';

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
    getQuestions: vi.fn(),
    saveSources: vi.fn(),
    saveQuestions: vi.fn(),
  };
});

vi.mock('@/api/document', () => ({
  getDocuments: vi.fn(),
}));

vi.mock('@/api/github', () => ({
  getRepositories: vi.fn(),
}));

const getApplicationMock = vi.mocked(getApplication);
const getQuestionsMock = vi.mocked(getQuestions);
const saveSourcesMock = vi.mocked(saveSources);
const saveQuestionsMock = vi.mocked(saveQuestions);
const getDocumentsMock = vi.mocked(getDocuments);
const getRepositoriesMock = vi.mocked(getRepositories);

describe('ApplicationDetailPage', () => {
  beforeEach(() => {
    paramsMock.mockReturnValue({ id: '1' });
    pushMock.mockReset();
    getApplicationMock.mockReset();
    getQuestionsMock.mockReset();
    saveSourcesMock.mockReset();
    saveQuestionsMock.mockReset();
    getDocumentsMock.mockReset();
    getRepositoriesMock.mockReset();

    getApplicationMock.mockResolvedValue({
      id: 1,
      applicationTitle: '2026 상반기 공채',
      companyName: '네이버',
      jobRole: '백엔드 개발자',
      status: 'draft',
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
        toneOption: null,
        lengthOption: 'medium',
        emphasisPoint: null,
      },
    ]);
    getDocumentsMock.mockResolvedValue([]);
    getRepositoriesMock.mockResolvedValue({
      data: [],
      pagination: {
        page: 0,
        size: 1000,
        totalElements: 0,
        totalPages: 0,
      },
    });
  });

  it('Step 3 CTA를 자소서 작성 화면 이동으로 노출한다', async () => {
    render(<ApplicationDetailPage />);

    expect(await screen.findByRole('button', { name: '자소서 작성 화면으로 이동' })).toBeInTheDocument();
    expect(screen.getByText('이 화면에서 소스를 연결하고 문항을 정리한 뒤, 자소서 작성 화면으로 이동합니다.')).toBeInTheDocument();
  });
});
