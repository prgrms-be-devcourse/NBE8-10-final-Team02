package com.back.backend.domain.interview.mapper;

import com.back.backend.domain.interview.dto.response.InterviewAnswerSubmitResponse;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionCurrentQuestionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionDetailResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionTransitionResponse;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewSession;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class InterviewResponseMapper {

    public InterviewQuestionResponse toInterviewQuestionResponse(InterviewQuestion question) {
        return new InterviewQuestionResponse(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType().getValue(),
                question.getDifficultyLevel().getValue(),
                question.getQuestionText(),
                question.getParentQuestion() == null ? null : question.getParentQuestion().getId(),
                question.getSourceApplicationQuestion() == null ? null : question.getSourceApplicationQuestion().getId()
        );
    }

    public InterviewSessionResponse toInterviewSessionResponse(InterviewSession session) {
        return new InterviewSessionResponse(
                session.getId(),
                session.getQuestionSet().getId(),
                session.getStatus().getValue(),
                session.getTotalScore(),
                session.getSummaryFeedback(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }

    public InterviewAnswerSubmitResponse toInterviewAnswerSubmitResponse(InterviewAnswer answer) {
        return new InterviewAnswerSubmitResponse(
                answer.getSession().getId(),
                answer.getQuestion().getId(),
                answer.getAnswerOrder(),
                answer.isSkipped(),
                answer.getCreatedAt()
        );
    }

    public InterviewSessionCurrentQuestionResponse toInterviewSessionCurrentQuestionResponse(InterviewQuestion question) {
        if (question == null) {
            return null;
        }

        return new InterviewSessionCurrentQuestionResponse(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType().getValue(),
                question.getDifficultyLevel().getValue(),
                question.getQuestionText()
        );
    }

    public InterviewSessionDetailResponse toInterviewSessionDetailResponse(
            InterviewSession session,
            InterviewQuestion currentQuestion,
            long totalQuestionCount,
            long answeredQuestionCount,
            long remainingQuestionCount,
            boolean resumeAvailable
    ) {
        return new InterviewSessionDetailResponse(
                session.getId(),
                session.getQuestionSet().getId(),
                session.getStatus().getValue(),
                toInterviewSessionCurrentQuestionResponse(currentQuestion),
                totalQuestionCount,
                answeredQuestionCount,
                remainingQuestionCount,
                resumeAvailable,
                session.getLastActivityAt(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }

    public InterviewSessionTransitionResponse toInterviewSessionTransitionResponse(
            InterviewSession session,
            Instant updatedAt
    ) {
        return new InterviewSessionTransitionResponse(
                session.getId(),
                session.getStatus().getValue(),
                updatedAt
        );
    }
}
