// 백엔드 ApplicationResponse와 1:1 대응
export interface Application {
  id: number;
  applicationTitle: string | null;
  companyName: string | null;
  jobRole: string;
  status: 'draft' | 'ready';
  createdAt: string;
  updatedAt: string;
  applicationType: string | null;
}

// POST /applications 요청 바디
export interface CreateApplicationRequest {
  applicationTitle?: string;
  companyName?: string;
  jobRole: string;
  applicationType?: string;
}

// 백엔드 ApplicationQuestionResponse와 1:1 대응
export interface ApplicationQuestion {
  id: number;
  questionOrder: number;
  questionText: string;
  generatedAnswer: string | null;
  editedAnswer: string | null;
  toneOption: string | null;
  lengthOption: string | null;
  emphasisPoint: string | null;
}

// 백엔드 ApplicationAnswerGenerationResponse와 1:1 대응
export interface GenerateAnswersResponse {
  applicationId: number;
  generatedCount: number;
  regenerate: boolean;
  answers: GeneratedAnswerItem[];
}

export interface GeneratedAnswerItem {
  questionId: number;
  questionText: string;
  generatedAnswer: string | null;
  toneOption: string | null;
  lengthOption: string | null;
}

// PUT /applications/{id}/sources 요청 바디
export interface SaveSourcesRequest {
  repositoryIds: number[];
  documentIds: number[];
}

// PUT /applications/{id}/sources 응답
export interface SourceBindingResponse {
  applicationId: number;
  repositoryIds: number[];
  documentIds: number[];
  sourceCount: number;
}

// POST /applications/{id}/questions 요청 바디
export interface SaveQuestionsRequest {
  questions: QuestionItem[];
}

export interface QuestionItem {
  questionOrder: number;
  questionText: string;
  toneOption?: string | null;
  lengthOption?: string | null;
  emphasisPoint?: string | null;
}

// POST /applications/{id}/questions/generate-answers 요청 바디
export interface GenerateAnswersRequest {
  useTemplate: boolean;
  regenerate: boolean;
}
