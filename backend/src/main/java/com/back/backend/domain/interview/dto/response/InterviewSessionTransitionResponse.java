package com.back.backend.domain.interview.dto.response;

import java.time.Instant;

public record InterviewSessionTransitionResponse(
        Long sessionId,
        String status,
        Instant updatedAt
) {
}
