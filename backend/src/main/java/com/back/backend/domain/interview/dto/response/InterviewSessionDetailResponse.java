package com.back.backend.domain.interview.dto.response;

import java.time.Instant;
import java.util.List;

public record InterviewSessionDetailResponse(
        Long id,
        Long questionSetId,
        String status,
        InterviewSessionCurrentQuestionResponse currentQuestion,
        InterviewSessionCompletionFollowupContextResponse completionFollowupContext,
        List<InterviewSessionTranscriptEntryResponse> transcriptEntries,
        long totalQuestionCount,
        long answeredQuestionCount,
        long remainingQuestionCount,
        boolean resumeAvailable,
        Instant lastActivityAt,
        Instant startedAt,
        Instant endedAt
) {
}
