package com.back.backend.domain.practice.dto.response;

import com.back.backend.domain.practice.entity.PracticeSession;

import java.time.Instant;
import java.util.List;

public record PracticeSessionDetailResponse(
        Long sessionId,
        String status,
        String questionTitle,
        String questionContent,
        String questionType,
        String answerText,
        Integer score,
        String feedback,
        String modelAnswer,
        List<String> tagNames,
        Instant evaluatedAt,
        Instant createdAt
) {

    public static PracticeSessionDetailResponse of(PracticeSession session, List<String> tagNames) {
        String questionText = PracticeQuestionResponse.buildCsQuestionTextFromSession(
                session.getKnowledgeItem().getTitle(),
                session.getKnowledgeItem().getContent(),
                session.getQuestionType()
        );
        return new PracticeSessionDetailResponse(
                session.getId(),
                session.getStatus().getValue(),
                questionText,
                session.getKnowledgeItem().getContent(),
                session.getQuestionType().getValue(),
                session.getAnswerText(),
                session.getScore(),
                session.getFeedback(),
                session.getModelAnswer(),
                tagNames,
                session.getEvaluatedAt(),
                session.getCreatedAt()
        );
    }
}
