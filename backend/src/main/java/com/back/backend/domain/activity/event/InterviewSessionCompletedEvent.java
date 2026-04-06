package com.back.backend.domain.activity.event;

public record InterviewSessionCompletedEvent(Long userId, Long sessionId) {
}
