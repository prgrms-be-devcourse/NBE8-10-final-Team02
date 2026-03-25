package com.back.backend.domain.interview.mapper;

import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionResponse;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewSession;
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
}
