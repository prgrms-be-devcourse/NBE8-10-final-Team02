package com.back.backend.domain.interview.dto.response;

public record InterviewSessionCompletionFollowupContextResponse(
        InterviewSessionCurrentQuestionResponse rootQuestion,
        InterviewSessionAnswerSummaryResponse rootAnswer,
        InterviewSessionCurrentQuestionResponse runtimeFollowupQuestion,
        InterviewSessionAnswerSummaryResponse runtimeFollowupAnswer,
        InterviewSessionCurrentQuestionResponse completionFollowupQuestion,
        int parentQuestionOrder
) {
}
