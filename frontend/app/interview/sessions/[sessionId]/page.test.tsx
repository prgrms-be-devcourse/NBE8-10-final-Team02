import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
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
  InterviewSessionCurrentQuestion,
  InterviewSessionDetail,
  InterviewSessionTranscriptEntry,
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
const scrollIntoViewMock = vi.fn();
const getUserMediaMock = vi.fn();

interface MockSpeechRecognitionResultEntry {
  transcript: string;
  isFinal: boolean;
}

class MockSpeechRecognition {
  static instances: MockSpeechRecognition[] = [];

  continuous = false;
  interimResults = false;
  lang = '';
  maxAlternatives = 1;
  onstart: (() => void) | null = null;
  onresult: ((event: { results: ArrayLike<{ isFinal: boolean; length: number; 0: { transcript: string } }> }) => void) | null = null;
  onerror: ((event: { error?: string }) => void) | null = null;
  onend: (() => void) | null = null;

  constructor() {
    MockSpeechRecognition.instances.push(this);
  }

  start() {
    this.onstart?.();
  }

  stop() {
    this.onend?.();
  }

  abort() {
    this.onend?.();
  }

  emitResult(entries: MockSpeechRecognitionResultEntry[]) {
    const results = entries.map((entry) => ({
      0: { transcript: entry.transcript },
      length: 1,
      isFinal: entry.isFinal,
    }));

    this.onresult?.({ results });
  }

  emitError(error?: string) {
    this.onerror?.({ error });
  }
}

function setUserAgent(value: string) {
  Object.defineProperty(window.navigator, 'userAgent', {
    configurable: true,
    value,
  });
}

function installSupportedVoiceBrowser() {
  MockSpeechRecognition.instances = [];
  setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36');
  Object.defineProperty(window.navigator, 'mediaDevices', {
    configurable: true,
    value: {
      getUserMedia: getUserMediaMock,
    },
  });
  Object.defineProperty(window, 'webkitSpeechRecognition', {
    configurable: true,
    value: MockSpeechRecognition,
  });
  Object.defineProperty(window, 'SpeechRecognition', {
    configurable: true,
    value: undefined,
  });
}

function installUnsupportedVoiceBrowser() {
  MockSpeechRecognition.instances = [];
  setUserAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7; rv:137.0) Gecko/20100101 Firefox/137.0');
  Object.defineProperty(window.navigator, 'mediaDevices', {
    configurable: true,
    value: undefined,
  });
  Object.defineProperty(window, 'webkitSpeechRecognition', {
    configurable: true,
    value: undefined,
  });
  Object.defineProperty(window, 'SpeechRecognition', {
    configurable: true,
    value: undefined,
  });
}

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

function createTranscriptEntry(
  overrides?: {
    question?: Partial<InterviewSessionTranscriptEntry['question']>;
    answer?: Partial<InterviewSessionTranscriptEntry['answer']>;
  },
): InterviewSessionTranscriptEntry {
  return {
    question: createCurrentQuestion({
      id: 77,
      questionOrder: 7,
      questionType: 'behavioral',
      difficultyLevel: 'medium',
      questionText: '이전 질문 기록',
      ...overrides?.question,
    }),
    answer: {
      answerOrder: 7,
      answerText: '이전 답변 기록',
      isSkipped: false,
      ...overrides?.answer,
    },
  };
}

function createSessionDetail(overrides?: Partial<InterviewSessionDetail>): InterviewSessionDetail {
  return {
    id: 1,
    questionSetId: 10,
    status: 'in_progress',
    currentQuestion: createCurrentQuestion(),
    completionFollowupContext: null,
    transcriptEntries: [],
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
  await screen.findByText('실시간 음성 모의 면접');
}

describe('InterviewSessionPage', () => {
  beforeEach(() => {
    installUnsupportedVoiceBrowser();
    paramsMock.mockReturnValue({ sessionId: '1' });
    pushMock.mockReset();
    getSessionDetailMock.mockReset();
    submitSessionAnswerMock.mockReset();
    completeSessionMock.mockReset();
    pauseSessionMock.mockReset();
    resumeSessionMock.mockReset();
    getUserMediaMock.mockReset();
    scrollIntoViewMock.mockReset();
    window.sessionStorage.clear();
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoViewMock,
    });
  });

  it('이전 기록이 있는 초기 진입에서는 transcript를 접고 현재 질문 카드를 입력창 바로 위에 보여준다', async () => {
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
        {
          id: 'answer-77',
          role: 'answer',
          text: '이전 답변 기록',
          questionId: 77,
          answerOrder: 7,
          isSkipped: false,
          pending: false,
          createdAt: '2026-04-07T10:01:00Z',
        },
      ]),
    );
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        transcriptEntries: [createTranscriptEntry()],
        currentQuestion: createCurrentQuestion({
          questionText: '지금 답해야 하는 현재 질문입니다.',
        }),
      }),
    );

    await renderPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '이전 문맥 펼치기' })).toBeInTheDocument();
    });
    expect(screen.getByText('질문 1개')).toBeInTheDocument();
    expect(screen.getByText('답변 1개')).toBeInTheDocument();
    expect(
      screen.getByText('이전 질문/답변 기록은 접혀 있습니다. 필요하면 위 버튼으로 다시 펼쳐 확인할 수 있습니다.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('이전 질문 기록')).not.toBeInTheDocument();
    const workRegion = screen.getByRole('region', { name: '현재 답변 작업' });
    expect(within(workRegion).getByText('현재 질문')).toBeInTheDocument();
    expect(within(workRegion).getByText('답변 입력')).toBeInTheDocument();
    expect(within(workRegion).queryByText('이전 문맥')).not.toBeInTheDocument();
    expect(screen.getByText('현재 질문')).toBeInTheDocument();
    expect(screen.getByText('이전 문맥')).toBeInTheDocument();
    expect(screen.getAllByText('지금 답해야 하는 현재 질문입니다.')).toHaveLength(1);

    const currentQuestionText = screen.getByText('지금 답해야 하는 현재 질문입니다.');
    const answerInput = screen.getByRole('textbox');
    expect(currentQuestionText.compareDocumentPosition(answerInput) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);
  });

  it('접힌 이전 문맥은 최근 상태 안내를 요약 정보와 함께 보여준다', async () => {
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
        {
          id: 'answer-77',
          role: 'answer',
          text: '이전 답변 기록',
          questionId: 77,
          answerOrder: 7,
          isSkipped: false,
          pending: false,
          createdAt: '2026-04-07T10:01:00Z',
        },
        {
          id: 'system-1',
          role: 'system',
          text: '세션을 재개했습니다. 이어서 답변을 제출할 수 있습니다.',
          tone: 'default',
          createdAt: '2026-04-07T10:02:00Z',
        },
      ]),
    );
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        transcriptEntries: [createTranscriptEntry()],
      }),
    );

    await renderPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '이전 문맥 펼치기' })).toBeInTheDocument();
    });
    expect(screen.getByText('질문 1개')).toBeInTheDocument();
    expect(screen.getByText('답변 1개')).toBeInTheDocument();
    expect(screen.getByText('상태 안내 1건')).toBeInTheDocument();
    expect(screen.getByText('상태 안내')).toBeInTheDocument();
    expect(screen.getByText('세션을 재개했습니다. 이어서 답변을 제출할 수 있습니다.')).toBeInTheDocument();
  });

  it('sessionStorage가 비어 있어도 서버 transcriptEntries로 이전 문맥을 복원한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        transcriptEntries: [
          createTranscriptEntry({
            question: {
              id: 55,
              questionOrder: 5,
              questionText: '서버에서 복원된 이전 질문입니다.',
            },
            answer: {
              answerOrder: 5,
              answerText: '서버에서 복원된 이전 답변입니다.',
              isSkipped: false,
            },
          }),
        ],
      }),
    );

    await renderPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '이전 문맥 펼치기' })).toBeInTheDocument();
    });
    expect(screen.getByText('질문 1개')).toBeInTheDocument();
    expect(screen.getByText('답변 1개')).toBeInTheDocument();
    expect(screen.queryByText('서버에서 복원된 이전 질문입니다.')).not.toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '이전 문맥 펼치기' }));

    expect(screen.getByText('서버에서 복원된 이전 질문입니다.')).toBeInTheDocument();
    expect(screen.getByText('서버에서 복원된 이전 답변입니다.')).toBeInTheDocument();
  });

  it('이전 문맥 펼침 패널은 세로 리사이즈 class를 가진다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        transcriptEntries: [createTranscriptEntry()],
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    const expandButton = screen.queryByRole('button', { name: '이전 문맥 펼치기' });
    if (expandButton) {
      await user.click(expandButton);
    }

    const transcriptPanel = screen.getByText('이전 질문 기록').closest('div[class*="resize-y"]');
    expect(transcriptPanel).toBeInTheDocument();
    expect(transcriptPanel?.className).toContain('h-[20rem]');
    expect(transcriptPanel?.className).toContain('min-h-[12rem]');
    expect(transcriptPanel?.className).toContain('max-h-[36rem]');
    expect(transcriptPanel?.className).toContain('resize-y');
  });

  it('이전 기록이 거의 없는 초기 상태에서는 이전 문맥 영역을 과하게 노출하지 않는다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());

    await renderPage();

    expect(screen.queryByText('이전 문맥')).not.toBeInTheDocument();
    expect(screen.getByText('현재 질문')).toBeInTheDocument();
    expect(screen.getAllByText('마지막 일반 질문입니다.')).toHaveLength(1);
  });

  it('지원 브라우저에서는 마이크 상태 배너와 음성 입력 시작 버튼을 보여준다', async () => {
    installSupportedVoiceBrowser();
    getUserMediaMock.mockResolvedValue({
      getTracks: () => [{ stop: vi.fn() }],
    });
    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());

    await renderPage();

    await waitFor(() => {
      expect(getUserMediaMock).toHaveBeenCalledTimes(1);
      expect(screen.getByText('음성 입력 가능')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '음성 입력 시작' })).toBeEnabled();
    });
    expect(
      screen.getByText('시작 후 답변하고, 중지한 뒤 내용을 확인하세요.'),
    ).toBeInTheDocument();
  });

  it('미지원 브라우저에서는 음성 UI를 fallback 안내로만 보여주고 textarea 입력을 유지한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());

    await renderPage();

    expect(screen.getByText('텍스트 입력')).toBeInTheDocument();
    expect(
      screen.getByText('Chrome, Edge 등 일부 브라우저에서만 음성 입력을 지원합니다. 현재는 텍스트 입력을 사용해주세요.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox')).toBeEnabled();
    expect(screen.getByRole('button', { name: '음성 입력 시작' })).toBeDisabled();
  });

  it('마이크 권한이 거부되면 같은 세션에서 텍스트 fallback과 권한 재요청 버튼을 유지한다', async () => {
    installSupportedVoiceBrowser();
    getUserMediaMock.mockRejectedValue(new DOMException('denied', 'NotAllowedError'));
    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());

    await renderPage();

    await waitFor(() => {
      expect(getUserMediaMock).toHaveBeenCalledTimes(1);
      expect(screen.getByText('권한 필요')).toBeInTheDocument();
    });
    expect(screen.getByText('마이크 권한을 켜주세요. 권한을 허용하면 음성 입력을 사용할 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '마이크 권한 다시 요청' })).toBeInTheDocument();
    expect(screen.getByRole('textbox')).toBeEnabled();
  });

  it('interim transcript는 별도 패널에만 보이고 최종 transcript는 수정 후 기존 answerText payload로 제출한다', async () => {
    installSupportedVoiceBrowser();
    getUserMediaMock.mockResolvedValue({
      getTracks: () => [{ stop: vi.fn() }],
    });
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
        currentQuestion: createCurrentQuestion({
          id: 102,
          questionOrder: 9,
          questionText: '다음 질문으로 넘어왔습니다. 이전 음성 transcript는 남지 않아야 합니다.',
        }),
        totalQuestionCount: 9,
        answeredQuestionCount: 8,
        remainingQuestionCount: 1,
      }),
    );

    await renderPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '음성 입력 시작' })).toBeEnabled();
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '음성 입력 시작' }));

    const recognition = MockSpeechRecognition.instances.at(-1);
    expect(recognition).toBeDefined();

    recognition?.emitResult([
      {
        transcript: '이 interim transcript는 별도 패널에만 보여야 하고 textarea에 바로 반영되면 안 됩니다.',
        isFinal: false,
      },
    ]);

    await waitFor(() => {
      expect(screen.getByText('실시간 interim transcript')).toBeInTheDocument();
      expect(
        screen.getByText('이 interim transcript는 별도 패널에만 보여야 하고 textarea에 바로 반영되면 안 됩니다.'),
      ).toBeInTheDocument();
    });
    expect(screen.getByRole('textbox')).toHaveValue('');

    recognition?.emitResult([
      {
        transcript: '이 최종 transcript는 입력란에 반영된 뒤 제출 전에 사용자가 문장을 조금 더 다듬을 수 있어야 합니다.',
        isFinal: true,
      },
    ]);
    await user.click(screen.getByRole('button', { name: '음성 입력 중지' }));

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toHaveValue(
        '이 최종 transcript는 입력란에 반영된 뒤 제출 전에 사용자가 문장을 조금 더 다듬을 수 있어야 합니다.',
      );
    });

    await user.type(screen.getByRole('textbox'), ' 여기에 마지막 수동 보완 문장을 덧붙입니다.');
    await user.click(screen.getByRole('button', { name: '제출' }));

    await screen.findByText('다음 질문으로 넘어왔습니다. 이전 음성 transcript는 남지 않아야 합니다.');

    expect(submitSessionAnswerMock).toHaveBeenCalledWith(1, {
      questionId: 101,
      answerOrder: 8,
      isSkipped: false,
      answerText: '이 최종 transcript는 입력란에 반영된 뒤 제출 전에 사용자가 문장을 조금 더 다듬을 수 있어야 합니다. 여기에 마지막 수동 보완 문장을 덧붙입니다.',
    });
    expect(
      screen.queryByText('이 interim transcript는 별도 패널에만 보여야 하고 textarea에 바로 반영되면 안 됩니다.'),
    ).not.toBeInTheDocument();
  });

  it('일반 답변 제출 후 다음 기본 질문으로 넘어가면 입력창으로 다시 포커스한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        transcriptEntries: [createTranscriptEntry()],
      }),
    );
    submitSessionAnswerMock.mockResolvedValueOnce({
      sessionId: 1,
      questionId: 101,
      answerOrder: 8,
      isSkipped: false,
      submittedAt: '2026-04-07T10:10:00Z',
    });
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: createCurrentQuestion({
          id: 102,
          questionOrder: 9,
          questionText: '다음 기본 질문입니다. 직전 사례의 결과를 설명해주세요.',
        }),
        totalQuestionCount: 9,
        answeredQuestionCount: 8,
        remainingQuestionCount: 1,
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    await user.type(
      screen.getByRole('textbox'),
      '이 답변은 다음 기본 질문으로 넘어간 뒤 입력 포커스가 자동으로 돌아오는지 확인하기 위한 충분한 길이의 일반 답변입니다.',
    );
    await user.click(screen.getByRole('button', { name: '제출' }));

    await screen.findByText('다음 기본 질문입니다. 직전 사례의 결과를 설명해주세요.');

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toHaveFocus();
    });
    expect(scrollIntoViewMock).toHaveBeenCalled();
  });

  it('마지막 일반 답변 제출 후 남은 질문이 없으면 자동으로 complete를 호출한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        transcriptEntries: [createTranscriptEntry()],
      }),
    );
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
        transcriptEntries: [createTranscriptEntry()],
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

    await screen.findByText('보완 질문 배경');

    expect(screen.getByRole('button', { name: '이전 문맥 접기' })).toBeInTheDocument();
    expect(screen.queryByText('이전 질문/답변 기록은 접혀 있습니다. 필요하면 위 버튼으로 다시 펼쳐 확인할 수 있습니다.')).not.toBeInTheDocument();
    expect(screen.getByText('보완 질문이 추가되었습니다. 이어서 답변을 제출해주세요.')).toBeInTheDocument();
    expect(screen.getByText('보완 질문 배경')).toBeInTheDocument();
    expect(screen.getByText('보완 질문')).toBeInTheDocument();
    expect(
      screen.getByText('이 질문은 이전 답변 전체를 바탕으로 AI가 추가 확인이 필요하다고 판단한 보완 질문입니다.'),
    ).toBeInTheDocument();
    const workRegion = screen.getByRole('region', { name: '현재 답변 작업' });
    expect(within(workRegion).getByText('현재 질문')).toBeInTheDocument();
    expect(within(workRegion).getByText('답변 입력')).toBeInTheDocument();
    expect(within(workRegion).queryByText('보완 질문 배경')).not.toBeInTheDocument();
    expect(screen.getByText('기준 질문 Q7')).toBeInTheDocument();
    expect(screen.getByText('이전 꼬리 질문 Q8')).toBeInTheDocument();
    expect(screen.getByText(completionContext.rootQuestion.questionText)).toBeInTheDocument();
    expect(screen.getAllByText(completionContext.completionFollowupQuestion.questionText)).toHaveLength(1);
    await waitFor(() => {
      expect(screen.getByRole('textbox')).toHaveFocus();
    });
    expect(scrollIntoViewMock).toHaveBeenCalled();
  });

  it('이전 문맥을 펼친 뒤 다음 질문으로 넘어가도 다시 자동으로 접지 않는다', async () => {
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
        transcriptEntries: [createTranscriptEntry()],
      }),
    );
    submitSessionAnswerMock.mockResolvedValueOnce({
      sessionId: 1,
      questionId: 101,
      answerOrder: 8,
      isSkipped: false,
      submittedAt: '2026-04-07T10:10:00Z',
    });
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        transcriptEntries: [createTranscriptEntry()],
        currentQuestion: createCurrentQuestion({
          id: 102,
          questionOrder: 9,
          questionText: '다음 질문으로 넘어왔습니다.',
        }),
        totalQuestionCount: 9,
        answeredQuestionCount: 8,
        remainingQuestionCount: 1,
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '이전 문맥 펼치기' })).toBeInTheDocument();
    });
    await user.click(screen.getByRole('button', { name: '이전 문맥 펼치기' }));
    expect(screen.getByText('이전 질문 기록')).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox'),
      '이 답변은 transcript를 한 번 펼친 뒤 다음 질문으로 넘어가도 자동으로 다시 접히지 않는지 확인하기 위한 충분한 길이의 일반 답변입니다.',
    );
    await user.click(screen.getByRole('button', { name: '제출' }));

    await screen.findByText('다음 질문으로 넘어왔습니다.');

    expect(screen.getByRole('button', { name: '이전 문맥 접기' })).toBeInTheDocument();
    expect(screen.getByText('이전 질문 기록')).toBeInTheDocument();
    expect(
      screen.queryByText('이전 질문/답변 기록은 접혀 있습니다. 필요하면 위 버튼으로 다시 펼쳐 확인할 수 있습니다.'),
    ).not.toBeInTheDocument();
  });

  it('이전 문맥을 펼치면 질문/답변/system bubble에 역할 라벨이 붙는다', async () => {
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
        {
          id: 'answer-77',
          role: 'answer',
          text: '이전 답변 기록',
          questionId: 77,
          answerOrder: 7,
          isSkipped: false,
          pending: false,
          createdAt: '2026-04-07T10:01:00Z',
        },
        {
          id: 'system-1',
          role: 'system',
          text: '세션을 일시정지했습니다. 같은 세션으로 다시 돌아와 재개할 수 있습니다.',
          tone: 'warning',
          createdAt: '2026-04-07T10:02:00Z',
        },
      ]),
    );
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        transcriptEntries: [createTranscriptEntry()],
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '이전 문맥 펼치기' })).toBeInTheDocument();
    });
    await user.click(screen.getByRole('button', { name: '이전 문맥 펼치기' }));

    expect(screen.getByText('면접관')).toBeInTheDocument();
    expect(screen.getByText('내 답변')).toBeInTheDocument();
    expect(screen.getByText('상태 안내')).toBeInTheDocument();
    expect(screen.getByText('이전 질문 기록')).toBeInTheDocument();
    expect(screen.getByText('이전 답변 기록')).toBeInTheDocument();
    expect(screen.getByText('현재 질문')).toBeInTheDocument();
    expect(screen.getByText('답변 입력')).toBeInTheDocument();
  });

  it('completion follow-up 진입 시에도 이전 문맥 펼침 상태를 덮어쓰지 않는다', async () => {
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
        transcriptEntries: [createTranscriptEntry()],
      }),
    );
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
        transcriptEntries: [createTranscriptEntry()],
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
      expect(screen.getByRole('button', { name: '이전 문맥 펼치기' })).toBeInTheDocument();
    });
    await user.click(screen.getByRole('button', { name: '이전 문맥 펼치기' }));
    expect(screen.getByText('이전 질문 기록')).toBeInTheDocument();
    expect(screen.getByText('면접관')).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox'),
      '이 답변은 completion follow-up으로 전환된 뒤에도 이전 문맥의 펼침 상태를 그대로 유지하는지 확인하기 위한 충분한 길이의 일반 답변입니다.',
    );
    await user.click(screen.getByRole('button', { name: '제출' }));

    await screen.findByText('보완 질문 배경');

    expect(screen.getByRole('button', { name: '이전 문맥 접기' })).toBeInTheDocument();
    expect(screen.getByText('이전 질문 기록')).toBeInTheDocument();
    expect(
      screen.queryByText('이전 질문/답변 기록은 접혀 있습니다. 필요하면 위 버튼으로 다시 펼쳐 확인할 수 있습니다.'),
    ).not.toBeInTheDocument();
  });

  it('건너뛰기로 다음 질문으로 넘어가도 입력창으로 다시 포커스한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());
    submitSessionAnswerMock.mockResolvedValueOnce({
      sessionId: 1,
      questionId: 101,
      answerOrder: 8,
      isSkipped: true,
      submittedAt: '2026-04-07T10:10:00Z',
    });
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: createCurrentQuestion({
          id: 103,
          questionOrder: 9,
          questionText: '건너뛴 뒤 이어지는 다음 질문입니다.',
        }),
        totalQuestionCount: 9,
        answeredQuestionCount: 8,
        remainingQuestionCount: 1,
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '건너뛰기' }));

    await screen.findByText('건너뛴 뒤 이어지는 다음 질문입니다.');

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toHaveFocus();
    });
    expect(scrollIntoViewMock).toHaveBeenCalled();
  });

  it('completion follow-up 초기 진입에서 이전 문맥 접기와 펼치기가 동작한다', async () => {
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
        transcriptEntries: [createTranscriptEntry()],
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
      expect(screen.getByRole('button', { name: '이전 문맥 펼치기' })).toBeInTheDocument();
    });
    expect(screen.getByText('이전 질문/답변 기록은 접혀 있습니다. 필요하면 위 버튼으로 다시 펼쳐 확인할 수 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText('이전 질문 기록')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '이전 문맥 펼치기' }));
    expect(screen.getByText('이전 문맥 접기')).toBeInTheDocument();
    expect(screen.getByText('이전 질문 기록')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '이전 문맥 접기' }));
    expect(screen.getByRole('button', { name: '이전 문맥 펼치기' })).toBeInTheDocument();
    expect(screen.queryByText('이전 질문 기록')).not.toBeInTheDocument();
  });

  it('일반 follow_up 질문은 completion context가 없으면 꼬리 질문 표시를 유지한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: createCurrentQuestion({
          id: 120,
          questionOrder: 5,
          questionType: 'follow_up',
          questionText: '직전 답변의 핵심 근거를 조금 더 구체적으로 설명해주세요.',
        }),
        completionFollowupContext: null,
      }),
    );

    await renderPage();

    expect(screen.getByText('꼬리 질문')).toBeInTheDocument();
    expect(screen.getByText('직전 답변을 더 구체화해 설명하는 꼬리 질문입니다.')).toBeInTheDocument();
    expect(screen.queryByText('보완 질문 배경')).not.toBeInTheDocument();
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

  it('진행 중 상태에서는 상단 요약 없이 세션 관리 카드에 상태 제어를 노출한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(createSessionDetail());

    await renderPage();

    expect(screen.queryByText('현재 일시정지된 세션입니다.')).not.toBeInTheDocument();
    expect(screen.getByText('세션 관리')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '일시정지' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '재개' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '세션 종료' })).toBeDisabled();
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

    await screen.findByText('세션을 재개했습니다. 이어서 답변을 제출할 수 있습니다.');
    expect(screen.queryByText('현재 일시정지된 세션입니다.')).not.toBeInTheDocument();
    expect(screen.getByText('세션 관리')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '일시정지' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '재개' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '세션 종료' })).toBeDisabled();
    expect(screen.getByRole('textbox')).not.toHaveFocus();
    expect(scrollIntoViewMock).not.toHaveBeenCalled();
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
    expect(screen.getAllByText('현재 일시정지된 세션입니다.')).toHaveLength(1);
    expect(screen.getByText('세션 관리')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '일시정지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '재개' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '세션 종료' })).toBeDisabled();
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
    expect(screen.getAllByText('자동 일시정지된 것으로 보입니다.')).toHaveLength(1);
    expect(screen.getByText('세션 관리')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '일시정지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '재개' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '세션 종료' })).toBeDisabled();
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
    expect(screen.getByRole('link', { name: '결과 재확인' })).toBeInTheDocument();
    expect(screen.queryByText('세션 관리')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '세션 종료' })).not.toBeInTheDocument();
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
    expect(screen.getByRole('link', { name: '결과 보기' })).toBeInTheDocument();
    expect(screen.queryByText('세션 관리')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '세션 종료' })).not.toBeInTheDocument();
  });

  it('세션 종료가 일반 오류를 반환해도 직후 재조회 상태가 terminal이면 결과 페이지로 이동한다', async () => {
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        currentQuestion: null,
        completionFollowupContext: null,
        totalQuestionCount: 8,
        answeredQuestionCount: 8,
        remainingQuestionCount: 0,
      }),
    );
    completeSessionMock.mockRejectedValueOnce(new Error('일시적인 오류가 발생했습니다.'));
    getSessionDetailMock.mockResolvedValueOnce(
      createSessionDetail({
        status: 'completed',
        currentQuestion: null,
        completionFollowupContext: null,
        totalQuestionCount: 8,
        answeredQuestionCount: 8,
        remainingQuestionCount: 0,
        endedAt: '2026-04-07T10:30:00Z',
      }),
    );

    await renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '세션 종료' }));

    await waitFor(() => {
      expect(completeSessionMock).toHaveBeenCalledTimes(1);
      expect(pushMock).toHaveBeenCalledWith('/interview/sessions/1/result');
    });
    expect(screen.queryByText('일시적인 오류가 발생했습니다.')).not.toBeInTheDocument();
  });
});
