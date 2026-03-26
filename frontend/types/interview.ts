export type InterviewDifficultyLevel = 'easy' | 'medium' | 'hard';

export type InterviewSessionStatus =
  | 'ready'
  | 'in_progress'
  | 'paused'
  | 'completed'
  | 'feedback_completed';

export type InterviewQuestionType =
  | 'experience'
  | 'project'
  | 'technical_cs'
  | 'technical_stack'
  | 'behavioral'
  | 'follow_up';

export interface InterviewQuestionSetCreateRequest {
  applicationId: number;
  title?: string;
  questionCount: number;
  difficultyLevel: InterviewDifficultyLevel;
  questionTypes: InterviewQuestionType[];
}

export interface InterviewQuestionSetSummary {
  questionSetId: number;
  applicationId: number;
  title: string;
  questionCount: number;
  difficultyLevel: InterviewDifficultyLevel;
  createdAt: string;
}

export interface InterviewQuestion {
  id: number;
  questionOrder: number;
  questionType: InterviewQuestionType;
  difficultyLevel: InterviewDifficultyLevel;
  questionText: string;
  parentQuestionId: number | null;
  sourceApplicationQuestionId: number | null;
}

export interface InterviewQuestionSetDetail extends InterviewQuestionSetSummary {
  questions: InterviewQuestion[];
}

export interface InterviewManualQuestionCreateRequest {
  questionText: string;
  questionType: Exclude<InterviewQuestionType, 'follow_up'>;
  difficultyLevel: InterviewDifficultyLevel;
}

export interface InterviewSession {
  id: number;
  questionSetId: number;
  status: InterviewSessionStatus;
  totalScore: number | null;
  summaryFeedback: string | null;
  startedAt: string | null;
  endedAt: string | null;
}

export interface InterviewSessionCurrentQuestion {
  id: number;
  questionOrder: number;
  questionType: InterviewQuestionType;
  difficultyLevel: InterviewDifficultyLevel;
  questionText: string;
}

export interface InterviewSessionDetail {
  id: number;
  questionSetId: number;
  status: InterviewSessionStatus;
  currentQuestion: InterviewSessionCurrentQuestion | null;
  totalQuestionCount: number;
  answeredQuestionCount: number;
  remainingQuestionCount: number;
  resumeAvailable: boolean;
  lastActivityAt: string | null;
  startedAt: string | null;
  endedAt: string | null;
}

export interface InterviewAnswerSubmitRequest {
  questionId: number;
  answerOrder: number;
  answerText?: string;
  isSkipped: boolean;
}

export interface SessionAnswerSubmitData {
  sessionId: number;
  questionId: number;
  answerOrder: number;
  isSkipped: boolean;
  submittedAt: string;
}

export interface ApiFieldError {
  field: string;
  reason: string;
}
