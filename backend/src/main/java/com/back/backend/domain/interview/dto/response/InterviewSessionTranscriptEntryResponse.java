package com.back.backend.domain.interview.dto.response;

public record InterviewSessionTranscriptEntryResponse(
        InterviewSessionCurrentQuestionResponse question,
        InterviewSessionAnswerSummaryResponse answer
) {
}
