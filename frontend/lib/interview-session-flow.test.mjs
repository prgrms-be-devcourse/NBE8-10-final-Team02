import test from 'node:test';
import assert from 'node:assert/strict';
import flow from './interview-session-flow.js';

const {
  hasRemainingIncompleteFieldError,
  shouldAutoCompleteAfterAnswer,
  shouldShowCompletionFollowupMode,
  shouldRequireManualCompleteAfterAnswer,
} = flow;

test('hasRemainingIncompleteFieldError returns true only for remainingQuestionCount incomplete', () => {
  assert.equal(hasRemainingIncompleteFieldError([{ field: 'remainingQuestionCount', reason: 'incomplete' }]), true);
  assert.equal(hasRemainingIncompleteFieldError([{ field: 'questionId', reason: 'missing' }]), false);
  assert.equal(hasRemainingIncompleteFieldError([]), false);
});

test('shouldAutoCompleteAfterAnswer triggers only after normal flow reaches zero remaining questions', () => {
  const refreshedSession = {
    status: 'in_progress',
    currentQuestion: null,
    remainingQuestionCount: 0,
  };

  assert.equal(shouldAutoCompleteAfterAnswer({ wasCompletionFollowup: false, refreshedSession }), true);
  assert.equal(shouldAutoCompleteAfterAnswer({ wasCompletionFollowup: true, refreshedSession }), false);
  assert.equal(
    shouldAutoCompleteAfterAnswer({
      wasCompletionFollowup: false,
      refreshedSession: { ...refreshedSession, currentQuestion: { id: 1 } },
    }),
    false,
  );
});

test('shouldShowCompletionFollowupMode requires incomplete error and context payload', () => {
  const refreshedSession = {
    completionFollowupContext: {
      parentQuestionOrder: 8,
    },
  };

  assert.equal(
    shouldShowCompletionFollowupMode({
      errorCode: 'REQUEST_VALIDATION_FAILED',
      fieldErrors: [{ field: 'remainingQuestionCount', reason: 'incomplete' }],
      refreshedSession,
    }),
    true,
  );
  assert.equal(
    shouldShowCompletionFollowupMode({
      errorCode: 'REQUEST_VALIDATION_FAILED',
      fieldErrors: [{ field: 'remainingQuestionCount', reason: 'incomplete' }],
      refreshedSession: { completionFollowupContext: null },
    }),
    false,
  );
});

test('shouldRequireManualCompleteAfterAnswer only turns on after completion follow-up answer', () => {
  const refreshedSession = {
    status: 'in_progress',
    currentQuestion: null,
    remainingQuestionCount: 0,
  };

  assert.equal(
    shouldRequireManualCompleteAfterAnswer({ wasCompletionFollowup: true, refreshedSession }),
    true,
  );
  assert.equal(
    shouldRequireManualCompleteAfterAnswer({ wasCompletionFollowup: false, refreshedSession }),
    false,
  );
});
