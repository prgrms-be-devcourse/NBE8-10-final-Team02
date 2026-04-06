package com.back.backend.domain.practice.dto.response;

import com.back.backend.domain.practice.entity.PracticeQuestionType;
import com.back.backend.domain.practice.entity.PracticeSession;

import java.time.Instant;
import java.util.List;

public record PracticeSessionResponse(
        Long sessionId,
        String status,
        String questionTitle,
        String questionType,
        Integer score,
        String feedback,
        String modelAnswer,
        List<String> tagNames,
        Instant evaluatedAt,
        Instant createdAt
) {

    public static PracticeSessionResponse of(PracticeSession session, List<String> tagNames) {
        String questionText = PracticeQuestionResponse.buildCsQuestionTextFromSession(
                session.getKnowledgeItem().getTitle(),
                session.getKnowledgeItem().getContent(),
                session.getQuestionType()
        );
        return new PracticeSessionResponse(
                session.getId(),
                session.getStatus().getValue(),
                questionText,
                session.getQuestionType().getValue(),
                session.getScore(),
                session.getFeedback(),
                session.getModelAnswer(),
                tagNames,
                session.getEvaluatedAt(),
                session.getCreatedAt()
        );
    }
}
