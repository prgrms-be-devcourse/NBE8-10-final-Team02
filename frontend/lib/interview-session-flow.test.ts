import { describe, expect, it } from 'vitest';
import {
  hasRemainingIncompleteFieldError,
  shouldAutoCompleteAfterAnswer,
  shouldRequireManualCompleteAfterAnswer,
  shouldShowCompletionFollowupMode,
} from './interview-session-flow';

describe('interview-session-flow', () => {
  it('hasRemainingIncompleteFieldError returns true only for remainingQuestionCount incomplete', () => {
    expect(hasRemainingIncompleteFieldError([{ field: 'remainingQuestionCount', reason: 'incomplete' }])).toBe(true);
    expect(hasRemainingIncompleteFieldError([{ field: 'questionId', reason: 'missing' }])).toBe(false);
    expect(hasRemainingIncompleteFieldError([])).toBe(false);
  });

  it('shouldAutoCompleteAfterAnswer triggers only after normal flow reaches zero remaining questions', () => {
    const refreshedSession = {
      status: 'in_progress',
      currentQuestion: null,
      remainingQuestionCount: 0,
    } as unknown as import('@/types/interview').InterviewSessionDetail;

    expect(shouldAutoCompleteAfterAnswer({ wasCompletionFollowup: false, refreshedSession })).toBe(true);
    expect(shouldAutoCompleteAfterAnswer({ wasCompletionFollowup: true, refreshedSession })).toBe(false);
    expect(
      shouldAutoCompleteAfterAnswer({
        wasCompletionFollowup: false,
        refreshedSession: { ...refreshedSession, currentQuestion: { id: 1 } } as unknown as import('@/types/interview').InterviewSessionDetail,
      }),
    ).toBe(false);
  });

  it('shouldShowCompletionFollowupMode requires incomplete error and context payload', () => {
    const refreshedSession = {
      completionFollowupContext: {
        parentQuestionOrder: 8,
      },
    } as unknown as import('@/types/interview').InterviewSessionDetail;

    expect(
      shouldShowCompletionFollowupMode({
        errorCode: 'REQUEST_VALIDATION_FAILED',
        fieldErrors: [{ field: 'remainingQuestionCount', reason: 'incomplete' }],
        refreshedSession,
      }),
    ).toBe(true);
    expect(
      shouldShowCompletionFollowupMode({
        errorCode: 'REQUEST_VALIDATION_FAILED',
        fieldErrors: [{ field: 'remainingQuestionCount', reason: 'incomplete' }],
        refreshedSession: { completionFollowupContext: null } as unknown as import('@/types/interview').InterviewSessionDetail,
      }),
    ).toBe(false);
  });

  it('shouldRequireManualCompleteAfterAnswer only turns on after completion follow-up answer', () => {
    const refreshedSession = {
      status: 'in_progress',
      currentQuestion: null,
      remainingQuestionCount: 0,
    } as unknown as import('@/types/interview').InterviewSessionDetail;

    expect(
      shouldRequireManualCompleteAfterAnswer({ wasCompletionFollowup: true, refreshedSession }),
    ).toBe(true);
    expect(
      shouldRequireManualCompleteAfterAnswer({ wasCompletionFollowup: false, refreshedSession }),
    ).toBe(false);
  });
});
