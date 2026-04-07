import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createApplication, deleteApplication, getApplications } from '@/api/application';
import ApplicationsPage from './page';

const pushMock = vi.fn();

vi.mock('next/navigation', () => ({
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
    getApplications: vi.fn(),
    createApplication: vi.fn(),
    deleteApplication: vi.fn(),
  };
});

const getApplicationsMock = vi.mocked(getApplications);
const createApplicationMock = vi.mocked(createApplication);
const deleteApplicationMock = vi.mocked(deleteApplication);

const applications = [
  {
    id: 1,
    applicationTitle: null,
    companyName: '네이버',
    jobRole: '백엔드 개발자',
    status: 'draft' as const,
    createdAt: '2026-04-07T00:00:00Z',
    updatedAt: '2026-04-07T00:00:00Z',
    applicationType: null,
  },
  {
    id: 2,
    applicationTitle: '2026 상반기 공채',
    companyName: '카카오',
    jobRole: '프론트엔드 개발자',
    status: 'ready' as const,
    createdAt: '2026-04-06T00:00:00Z',
    updatedAt: '2026-04-06T00:00:00Z',
    applicationType: null,
  },
];

describe('ApplicationsPage', () => {
  beforeEach(() => {
    pushMock.mockReset();
    getApplicationsMock.mockReset();
    createApplicationMock.mockReset();
    deleteApplicationMock.mockReset();
    getApplicationsMock.mockResolvedValue(applications);
  });

  it('draft와 ready 상태에 맞는 CTA를 노출하고 목록 레벨 자소서 생성 버튼을 제거한다', async () => {
    render(<ApplicationsPage />);

    await screen.findByRole('button', { name: '자소서 이어쓰기' });

    expect(screen.getByRole('button', { name: '자소서 이어쓰기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '면접 준비' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '자소서 생성' })).not.toBeInTheDocument();
  });

  it('카드 본문 클릭은 상세로 이동하고 삭제 버튼 클릭은 카드 이동으로 전파되지 않는다', async () => {
    render(<ApplicationsPage />);

    await screen.findByRole('button', { name: '자소서 이어쓰기' });

    const user = userEvent.setup();
    const draftCard = screen.getByRole('button', { name: '자소서 이어쓰기' }).closest('li');

    expect(draftCard).not.toBeNull();

    await user.click(within(draftCard as HTMLElement).getByRole('button', { name: '삭제' }));
    expect(pushMock).not.toHaveBeenCalled();

    await user.click(within(draftCard as HTMLElement).getByText('네이버'));
    expect(pushMock).toHaveBeenCalledWith('/applications/1');
  });
});
