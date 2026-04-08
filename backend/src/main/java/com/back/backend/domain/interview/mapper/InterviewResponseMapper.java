package com.back.backend.domain.interview.mapper;

import com.back.backend.domain.interview.dto.response.InterviewAnswerResultResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionAnswerSummaryResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionCompletionFollowupContextResponse;
import com.back.backend.domain.interview.dto.response.InterviewAnswerSubmitResponse;
import com.back.backend.domain.interview.dto.response.InterviewFeedbackTagResponse;
import com.back.backend.domain.interview.dto.response.InterviewQuestionResponse;
import com.back.backend.domain.interview.dto.response.InterviewResultResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionCompletionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionCurrentQuestionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionDetailResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionTranscriptEntryResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionTransitionResponse;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewAnswerTag;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewSessionQuestion;
import com.back.backend.domain.interview.entity.InterviewSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
                answer.getSessionQuestion().getId(),
                answer.getAnswerOrder(),
                answer.isSkipped(),
                answer.getCreatedAt()
        );
    }

    public InterviewSessionCurrentQuestionResponse toInterviewSessionCurrentQuestionResponse(InterviewSessionQuestion question) {
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
            InterviewSessionQuestion currentQuestion,
            InterviewSessionCompletionFollowupContextResponse completionFollowupContext,
            List<InterviewSessionTranscriptEntryResponse> transcriptEntries,
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
                completionFollowupContext,
                transcriptEntries,
                totalQuestionCount,
                answeredQuestionCount,
                remainingQuestionCount,
                resumeAvailable,
                session.getLastActivityAt(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }

    public InterviewSessionAnswerSummaryResponse toInterviewSessionAnswerSummaryResponse(InterviewAnswer answer) {
        if (answer == null) {
            return null;
        }

        return new InterviewSessionAnswerSummaryResponse(
                answer.getAnswerOrder(),
                answer.getAnswerText(),
                answer.isSkipped()
        );
    }

    public InterviewSessionTranscriptEntryResponse toInterviewSessionTranscriptEntryResponse(InterviewAnswer answer) {
        if (answer == null) {
            return null;
        }

        return new InterviewSessionTranscriptEntryResponse(
                toInterviewSessionCurrentQuestionResponse(answer.getSessionQuestion()),
                toInterviewSessionAnswerSummaryResponse(answer)
        );
    }

    public InterviewSessionCompletionFollowupContextResponse toInterviewSessionCompletionFollowupContextResponse(
            InterviewSessionQuestion rootQuestion,
            InterviewAnswer rootAnswer,
            InterviewSessionQuestion runtimeFollowupQuestion,
            InterviewAnswer runtimeFollowupAnswer,
            InterviewSessionQuestion completionFollowupQuestion,
            int parentQuestionOrder
    ) {
        return new InterviewSessionCompletionFollowupContextResponse(
                toInterviewSessionCurrentQuestionResponse(rootQuestion),
                toInterviewSessionAnswerSummaryResponse(rootAnswer),
                toInterviewSessionCurrentQuestionResponse(runtimeFollowupQuestion),
                toInterviewSessionAnswerSummaryResponse(runtimeFollowupAnswer),
                toInterviewSessionCurrentQuestionResponse(completionFollowupQuestion),
                parentQuestionOrder
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

    public InterviewSessionCompletionResponse toInterviewSessionCompletionResponse(InterviewSession session) {
        return new InterviewSessionCompletionResponse(
                session.getId(),
                session.getStatus().getValue(),
                session.getTotalScore(),
                session.getSummaryFeedback(),
                session.getEndedAt()
        );
    }

    public InterviewResultResponse toInterviewResultResponse(
            InterviewSession session,
            List<InterviewAnswer> answers,
            Map<Long, List<InterviewAnswerTag>> tagsByAnswerId
    ) {
        return new InterviewResultResponse(
                session.getId(),
                session.getQuestionSet().getId(),
                session.getStatus().getValue(),
                session.getTotalScore(),
                session.getSummaryFeedback(),
                answers.stream()
                        .map(answer -> toInterviewAnswerResultResponse(
                                answer,
                                tagsByAnswerId.getOrDefault(answer.getId(), List.of())
                        ))
                        .toList(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }

    private InterviewAnswerResultResponse toInterviewAnswerResultResponse(
            InterviewAnswer answer,
            List<InterviewAnswerTag> answerTags
    ) {
        return new InterviewAnswerResultResponse(
                answer.getId(),
                answer.getSessionQuestion().getId(),
                answer.getSessionQuestion().getQuestionType().getValue(),
                answer.getSessionQuestion().getQuestionText(),
                answer.getAnswerText(),
                answer.getScore(),
                answer.getEvaluationRationale(),
                answerTags.stream()
                        .map(InterviewAnswerTag::getTag)
                        .map(this::toInterviewFeedbackTagResponse)
                        .toList()
        );
    }

    private InterviewFeedbackTagResponse toInterviewFeedbackTagResponse(FeedbackTag tag) {
        return new InterviewFeedbackTagResponse(
                tag.getId(),
                tag.getTagName(),
                tag.getTagCategory().getValue()
        );
    }
}
