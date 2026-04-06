import type { ApiFieldError, InterviewSessionDetail } from '@/types/interview';

interface CompletionFollowupModeOptions {
  errorCode: string | null;
  fieldErrors: ApiFieldError[];
  refreshedSession: InterviewSessionDetail | null;
}

interface AutoCompleteAfterAnswerOptions {
  wasCompletionFollowup: boolean;
  refreshedSession: InterviewSessionDetail | null;
}

export function hasRemainingIncompleteFieldError(fieldErrors: ApiFieldError[]) {
  return Array.isArray(fieldErrors)
    && fieldErrors.some((fieldError) =>
      fieldError?.field === 'remainingQuestionCount' && fieldError?.reason === 'incomplete');
}

export function shouldAutoCompleteAfterAnswer({
  wasCompletionFollowup,
  refreshedSession,
}: AutoCompleteAfterAnswerOptions) {
  return !wasCompletionFollowup
    && !!refreshedSession
    && refreshedSession.status === 'in_progress'
    && refreshedSession.currentQuestion === null
    && refreshedSession.remainingQuestionCount === 0;
}

export function shouldShowCompletionFollowupMode({
  errorCode,
  fieldErrors,
  refreshedSession,
}: CompletionFollowupModeOptions) {
  return errorCode === 'REQUEST_VALIDATION_FAILED'
    && hasRemainingIncompleteFieldError(fieldErrors)
    && !!refreshedSession?.completionFollowupContext;
}

export function shouldRequireManualCompleteAfterAnswer({
  wasCompletionFollowup,
  refreshedSession,
}: AutoCompleteAfterAnswerOptions) {
  return !!wasCompletionFollowup
    && !!refreshedSession
    && refreshedSession.status === 'in_progress'
    && refreshedSession.currentQuestion === null
    && refreshedSession.remainingQuestionCount === 0;
}

const flow = {
  hasRemainingIncompleteFieldError,
  shouldAutoCompleteAfterAnswer,
  shouldShowCompletionFollowupMode,
  shouldRequireManualCompleteAfterAnswer,
};

export default flow;
