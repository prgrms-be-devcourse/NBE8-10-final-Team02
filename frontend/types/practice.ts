export type PracticeQuestionType = 'cs' | 'behavioral';
export type PracticeSessionStatus = 'in_progress' | 'evaluated' | 'failed';

export interface PracticeTag {
  id: number;
  name: string;
  category: string;
}

export interface PracticeQuestion {
  knowledgeItemId: number;
  title: string;
  questionText: string;
  questionType: PracticeQuestionType;
  tags: PracticeTag[];
}

export interface SubmitPracticeAnswerRequest {
  knowledgeItemId: number;
  answerText: string;
}

export interface PracticeSessionResponse {
  sessionId: number;
  status: PracticeSessionStatus;
  questionTitle: string;
  questionType: PracticeQuestionType;
  score: number | null;
  feedback: string | null;
  modelAnswer: string | null;
  tagNames: string[];
  evaluatedAt: string | null;
  createdAt: string;
}

export interface PracticeSessionDetailResponse extends PracticeSessionResponse {
  questionContent: string;
  answerText: string;
}

export interface PracticePagination {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
