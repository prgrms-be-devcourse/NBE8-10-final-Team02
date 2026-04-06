function hasRemainingIncompleteFieldError(fieldErrors) {
  return Array.isArray(fieldErrors)
    && fieldErrors.some((fieldError) =>
      fieldError?.field === 'remainingQuestionCount' && fieldError?.reason === 'incomplete');
}

function shouldAutoCompleteAfterAnswer({
  wasCompletionFollowup,
  refreshedSession,
}) {
  return !wasCompletionFollowup
    && !!refreshedSession
    && refreshedSession.status === 'in_progress'
    && refreshedSession.currentQuestion === null
    && refreshedSession.remainingQuestionCount === 0;
}

function shouldShowCompletionFollowupMode({
  errorCode,
  fieldErrors,
  refreshedSession,
}) {
  return errorCode === 'REQUEST_VALIDATION_FAILED'
    && hasRemainingIncompleteFieldError(fieldErrors)
    && !!refreshedSession?.completionFollowupContext;
}

function shouldRequireManualCompleteAfterAnswer({
  wasCompletionFollowup,
  refreshedSession,
}) {
  return !!wasCompletionFollowup
    && !!refreshedSession
    && refreshedSession.status === 'in_progress'
    && refreshedSession.currentQuestion === null
    && refreshedSession.remainingQuestionCount === 0;
}

module.exports = {
  hasRemainingIncompleteFieldError,
  shouldAutoCompleteAfterAnswer,
  shouldShowCompletionFollowupMode,
  shouldRequireManualCompleteAfterAnswer,
};
