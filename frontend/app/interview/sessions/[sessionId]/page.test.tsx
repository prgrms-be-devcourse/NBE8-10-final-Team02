import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  completeSession,
  getSessionDetail,
  InterviewApiError,
  pauseSession,
  resumeSession,
  submitSessionAnswer,
} from '@/api/interview';
import type {
  CompletionFollowupContext,
  InterviewQuestionType,
  InterviewSessionCurrentQuestion,
  InterviewSessionDetail,
} from '@/types/interview';
import InterviewSessionPage from './page';

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
    getSessionDetail: vi.fn(),
    submitSessionAnswer: vi.fn(),
    completeSession: vi.fn(),
    pauseSession: vi.fn(),
    resumeSession: vi.fn(),
  };
});

const getSessionDetailMock = vi.mocked(getSessionDetail);
const submitSessionAnswerMock = vi.mocked(submitSessionAnswer);
const completeSessionMock = vi.mocked(completeSession);
const pauseSessionMock = vi.mocked(pauseSession);
const resumeSessionMock = vi.mocked(resumeSession);

function createCurrentQuestion(
  overrides?: Partial<InterviewSessionCurrentQuestion>,
): InterviewSessionCurrentQuestion {
  return {
    id: 101,
    questionOrder: 8,
    questionType: 'behavioral',
    difficultyLevel: 'medium',
    questionText: '마지막 일반 질문입니다.',
    ...overrides,
  };
}

function createCompletionContext(): CompletionFollowupContext {
  return {
    rootQuestion: {
      id: 71,
      questionOrder: 7,
      questionType: 'behavioral',
      difficultyLevel: 'medium',
      questionText: '의견 충돌을 해결한 경험을 설명해주세요.',
    },
    rootAnswer: {
      answerOrder: 7,
      answerText: '장단점 비교와 작은 검증을 통해 합의점을 찾았습니다.',
      isSkipped: false,
    },
    runtimeFollowupQuestion: {
      id: 81,
      questionOrder: 8,
      questionType: 'follow_up',
      difficultyLevel: 'medium',
      questionText: '그 문제의 근본 원인을 어떻게 파악했는지 설명해주세요.',
    },
    runtimeFollowupAnswer: {
      answerOrder: 8,
      answerText: '실패 로그를 유형별로 분리하고 재현 테스트를 수행했습니다.',
      isSkipped: false,
    },
    completionFollowupQuestion: {
      id: 91,
      questionOrder: 9,
      questionType: 'follow_up',
      difficultyLevel: 'medium',
      questionText: '그 원인을 어떤 방식으로 검증했는지 조금 더 구체적으로 설명해주실 수 있나요?',
    },
    parentQuestionOrder: 8,
  };
}

function createSessionDetail(overrides?: Partial<InterviewSessionDetail>): InterviewSessionDetail {
  return {
    id: 1,
    questionSetId: 10,
    status: 'in_progress',
    currentQuestion: createCurrentQuestion(),
    completionFollowupContext: null,
    totalQuestionCount: 8,
    answeredQuestionCount: 7,
    remainingQuestionCount: 1,
    resumeAvailable: true,
    lastActivityAt: '2026-04-07T10:00:00Z',
    startedAt: '2026-04-07T09:00:00Z',
    endedAt: null,
    ...overrides,
  };
}

function createIncompleteError() {
  return new InterviewApiError('보완 질문이 추가되었습니다.', {
    code: 'REQUEST_VALIDATION_FAILED',
    fieldErrors: [{ field: 'remainingQuestionCount', reason: 'incomplete' }],
  });
}

async function renderPage() {
  render(<InterviewSessionPage />);
  await screen.findByText('텍스트 모의 면접');
}

describe('InterviewSessionPage', () => {
  beforeEach(() => {
    paramsMock.mockReturnValue({ sessionId: '1' });
    pushMock.mockReset();
    getSessionDetailMock.mockReset();
    submitSessionAnswerMock.mockReset();
    completeSessionMock.mockReset();
    pauseSessionMock.mockReset();
    resumeSessionMock.mockReset();
    window.sessionStorage.clear();
  });

  it('마지막 일반 답변 제출 후 남은 질문이 없으면 자동으로 complete를 호출한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());
    submitSessionAnswerMock.mockResolvedValueOnce({
      sessionId: 1,
      questionId: 101,
      answerOrder: 8,
      isSkipped: false,
      submittedAt: '2026-04-07T10:10:00Z',
    });
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: null,
        completionFollowupContext: null,
        answeredQuestionCount: 8,
        remainingQuestionCount: 0,
      }),
    );
    completeSessionMock.mockResolvedValueOnce({
      sessionId: 1,
      status: 'feedback_completed',
      totalScore: 82,
      summaryFeedback: '좋습니다.',
      endedAt: '2026-04-07T10:11:00Z',
    });

    await renderPage();

    const user = userEvent.setup();
    await user.type(
      screen.getByRole('textbox'),
      '이 답변은 자동 종료 검토를 테스트하기 위한 충분한 길이의 일반 답변입니다. 마지막 질문에 대한 응답을 저장합니다.',
    );
    await user.click(screen.getByRole('button', { name: '제출' }));

    await waitFor(() => {
      expect(submitSessionAnswerMock).toHaveBeenCalledTimes(1);
      expect(completeSessionMock).toHaveBeenCalledTimes(1);
    });

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith('/interview/sessions/1/result');
    });
  });

  it('complete가 incomplete를 반환하면 completion follow-up 카드로 전환한다', async () => {
    const completionContext = createCompletionContext();

    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());
    submitSessionAnswerMock.mockResolvedValueOnce({
      sessionId: 1,
      questionId: 101,
      answerOrder: 8,
      isSkipped: false,
      submittedAt: '2026-04-07T10:10:00Z',
    });
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: null,
        completionFollowupContext: null,
        answeredQuestionCount: 8,
        remainingQuestionCount: 0,
      }),
    );
    completeSessionMock.mockRejectedValueOnce(createIncompleteError());
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: completionContext.completionFollowupQuestion,
        completionFollowupContext: completionContext,
        totalQuestionCount: 9,
        answeredQuestionCount: 8,
        remainingQuestionCount: 1,
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    await user.type(
      screen.getByRole('textbox'),
      '자동 complete 이후 보완 질문 전환을 확인하기 위한 마지막 일반 답변입니다. 충분한 길이로 작성합니다.',
    );
    await user.click(screen.getByRole('button', { name: '제출' }));

    await screen.findByText('마지막 보완 질문');

    expect(screen.getByText('기존 채팅 펼치기')).toBeInTheDocument();
    expect(screen.getByText('기존 질문/답변 기록은 접혀 있습니다. 필요하면 위 버튼으로 다시 펼쳐 확인할 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByText('마지막 보완 질문이 추가되었습니다. 이어서 답변을 제출해주세요.')).toBeInTheDocument();
    expect(screen.getByText(completionContext.rootQuestion.questionText)).toBeInTheDocument();
    expect(screen.getByText(completionContext.completionFollowupQuestion.questionText)).toBeInTheDocument();
  });

  it('completion follow-up 모드에서 transcript 접기와 펼치기가 동작한다', async () => {
    const completionContext = createCompletionContext();
    window.sessionStorage.setItem(
      'interview-session-chat:1',
      JSON.stringify([
        {
          id: 'question-77',
          role: 'question',
          text: '이전 질문 기록',
          questionId: 77,
          questionOrder: 7,
          questionType: 'behavioral',
          difficultyLevel: 'medium',
          createdAt: '2026-04-07T10:00:00Z',
        },
      ]),
    );

    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: completionContext.completionFollowupQuestion,
        completionFollowupContext: completionContext,
        totalQuestionCount: 9,
        answeredQuestionCount: 8,
        remainingQuestionCount: 1,
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '기존 채팅 펼치기' })).toBeInTheDocument();
    });
    expect(screen.getByText('기존 질문/답변 기록은 접혀 있습니다. 필요하면 위 버튼으로 다시 펼쳐 확인할 수 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText('이전 질문 기록')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '기존 채팅 펼치기' }));
    expect(screen.getByText('기존 채팅 접기')).toBeInTheDocument();
    expect(screen.getByText('이전 질문 기록')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '기존 채팅 접기' }));
    expect(screen.getByRole('button', { name: '기존 채팅 펼치기' })).toBeInTheDocument();
    expect(screen.queryByText('이전 질문 기록')).not.toBeInTheDocument();
  });

  it('completion follow-up 답변 제출 후에는 수동 종료 배너를 보여주고 명시적 종료 시 결과로 이동한다', async () => {
    const completionContext = createCompletionContext();

    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: completionContext.completionFollowupQuestion,
        completionFollowupContext: completionContext,
        totalQuestionCount: 9,
        answeredQuestionCount: 8,
        remainingQuestionCount: 1,
      }),
    );
    submitSessionAnswerMock.mockResolvedValueOnce({
      sessionId: 1,
      questionId: completionContext.completionFollowupQuestion.id,
      answerOrder: 9,
      isSkipped: false,
      submittedAt: '2026-04-07T10:12:00Z',
    });
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: null,
        completionFollowupContext: null,
        totalQuestionCount: 9,
        answeredQuestionCount: 9,
        remainingQuestionCount: 0,
      }),
    );
    completeSessionMock.mockResolvedValueOnce({
      sessionId: 1,
      status: 'feedback_completed',
      totalScore: 85,
      summaryFeedback: '결과가 준비되었습니다.',
      endedAt: '2026-04-07T10:13:00Z',
    });

    await renderPage();

    const user = userEvent.setup();
    await user.type(
      screen.getByRole('textbox'),
      '이 보완 답변은 completion follow-up 제출 이후에는 자동 종료하지 않고 다시 종료 버튼을 누르게 하는 흐름을 검증합니다.',
    );
    await user.click(screen.getByRole('button', { name: '제출' }));

    await screen.findByText('세션 종료를 다시 눌러 결과를 생성하세요.');
    expect(completeSessionMock).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: '세션 종료' }));

    await waitFor(() => {
      expect(completeSessionMock).toHaveBeenCalledTimes(1);
      expect(pushMock).toHaveBeenCalledWith('/interview/sessions/1/result');
    });
  });

  it('재개 성공 후 상단 상태 요약이 즉시 진행 상태로 갱신된다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        status: 'paused',
        lastActivityAt: new Date().toISOString(),
      }),
    );
    resumeSessionMock.mockResolvedValueOnce({
      sessionId: 1,
      status: 'in_progress',
      updatedAt: '2026-04-07T10:20:00Z',
    });
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        status: 'in_progress',
        currentQuestion: createCurrentQuestion({
          id: 111,
          questionOrder: 2,
          questionText: '재개 후 같은 질문부터 이어서 답변해주세요.',
        }),
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '재개' }));

    await screen.findByText('세션을 재개했습니다.');
    expect(
      screen.getByText('방금 재개되어 같은 질문부터 다시 이어서 제출할 수 있습니다. 다음 질문은 항상 서버가 내려주는 currentQuestion 기준으로 바뀝니다.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '일시정지' })).toBeInTheDocument();
  });

  it('수동 paused 상태와 자동 일시정지 추정 상태를 다른 문구로 보여준다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        status: 'paused',
        lastActivityAt: new Date().toISOString(),
      }),
    );

    await renderPage();

    expect(screen.getByText('수동 일시정지')).toBeInTheDocument();
    expect(screen.getAllByText('현재 일시정지된 세션입니다.').length).toBeGreaterThan(0);
    expect(screen.queryByText('자동 일시정지된 것으로 보입니다.')).not.toBeInTheDocument();
  });

  it('자동 일시정지로 보이는 paused 상태를 별도 안내로 보여준다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        status: 'paused',
        lastActivityAt: '2020-01-01T00:00:00Z',
      }),
    );

    await renderPage();

    expect(screen.getByText('자동 일시정지')).toBeInTheDocument();
    expect(screen.getAllByText('자동 일시정지된 것으로 보입니다.').length).toBeGreaterThan(0);
    expect(
      screen.getByText('마지막 활동 이후 30분 이상 지나 서버가 paused로 정규화했을 가능성이 높습니다. 재개 후 같은 질문부터 다시 이어서 진행하세요.'),
    ).toBeInTheDocument();
  });

  it('completed 상태와 feedback_completed 상태를 다른 결과 안내로 구분한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        status: 'completed',
        currentQuestion: null,
        remainingQuestionCount: 0,
        endedAt: '2026-04-07T10:30:00Z',
      }),
    );

    await renderPage();

    expect(screen.getByText('상태 결과 재확인')).toBeInTheDocument();
    expect(screen.getByText('세션은 종료되었고 결과를 다시 확인할 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '결과 재확인' })).toBeInTheDocument();
    expect(
      screen.getByText('세션은 종료됐고 결과는 재확인 흐름으로 확인합니다. 세션 종료를 다시 보내지 않고 결과 화면에서 최신 상태를 조회하세요.'),
    ).toBeInTheDocument();
  });

  it('feedback_completed 상태에서는 결과 완료 안내와 결과 보기 액션을 노출한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        status: 'feedback_completed',
        currentQuestion: null,
        remainingQuestionCount: 0,
        endedAt: '2026-04-07T10:32:00Z',
      }),
    );

    await renderPage();

    expect(screen.getByText('상태 피드백 완료')).toBeInTheDocument();
    expect(screen.getByText('피드백 리포트가 준비되었습니다.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '결과 보기' })).toBeInTheDocument();
    expect(screen.getByText('결과 리포트가 준비되었습니다. 결과 보기 버튼으로 이동하세요.')).toBeInTheDocument();
  });
});
