package com.back.backend.domain.interview.dto.response;

public record InterviewFeedbackTagResponse(
        Long tagId,
        String tagName,
        String tagCategory
) {
}
