package com.back.backend.domain.interview.service;

import com.back.backend.domain.interview.dto.request.SubmitInterviewAnswerRequest;
import com.back.backend.domain.interview.dto.response.InterviewAnswerSubmitResponse;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.mapper.InterviewResponseMapper;
import com.back.backend.domain.interview.repository.InterviewAnswerRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.response.FieldErrorDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewAnswerService {

    private static final Duration AUTO_PAUSE_THRESHOLD = Duration.ofMinutes(30);

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewAnswerRepository interviewAnswerRepository;
    private final InterviewResponseMapper interviewResponseMapper;

    @Transactional(noRollbackFor = ServiceException.class)
    public InterviewAnswerSubmitResponse submitAnswer(
            long userId,
            long sessionId,
            SubmitInterviewAnswerRequest request
    ) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "세션 또는 질문을 찾을 수 없습니다."
                ));

        validateSessionStatus(session);

        long questionId = requirePositiveLong(request.questionId(), "questionId");
        int answerOrder = requirePositiveInt(request.answerOrder(), "answerOrder");
        int expectedAnswerOrder = Math.toIntExact(interviewAnswerRepository.countBySessionId(sessionId) + 1L);

        InterviewQuestion requestedQuestion = interviewQuestionRepository.findByIdAndQuestionSetId(
                        questionId,
                        session.getQuestionSet().getId()
                )
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "세션 또는 질문을 찾을 수 없습니다."
                ));

        InterviewQuestion currentQuestion = interviewQuestionRepository.findByQuestionSetIdAndQuestionOrder(
                        session.getQuestionSet().getId(),
                        expectedAnswerOrder
                )
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.REQUEST_VALIDATION_FAILED,
                        HttpStatus.BAD_REQUEST,
                        "요청 값을 다시 확인해주세요.",
                        false,
                        List.of(new FieldErrorDetail("questionId", "not_current"))
                ));

        if (!currentQuestion.getId().equals(requestedQuestion.getId()) || answerOrder != expectedAnswerOrder) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(
                            new FieldErrorDetail("questionId", "not_current"),
                            new FieldErrorDetail("answerOrder", "out_of_sequence")
                    )
            );
        }

        String normalizedAnswerText = normalizeAnswerText(request.answerText(), request.isSkipped());

        InterviewAnswer savedAnswer = interviewAnswerRepository.save(
                InterviewAnswer.builder()
                        .session(session)
                        .question(requestedQuestion)
                        .answerOrder(expectedAnswerOrder)
                        .answerText(normalizedAnswerText)
                        .skipped(request.isSkipped())
                        .build()
        );

        return interviewResponseMapper.toInterviewAnswerSubmitResponse(savedAnswer);
    }

    private void validateSessionStatus(InterviewSession session) {
        InterviewSessionStatus status = session.getStatus();
        if (status == InterviewSessionStatus.COMPLETED || status == InterviewSessionStatus.FEEDBACK_COMPLETED) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED,
                    HttpStatus.CONFLICT,
                    "이미 종료된 면접 세션입니다."
            );
        }

        if (status == InterviewSessionStatus.IN_PROGRESS && shouldAutoPause(session)) {
            session.changeStatus(InterviewSessionStatus.PAUSED);
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_NOT_ACTIVE,
                    HttpStatus.CONFLICT,
                    "진행 가능한 면접 세션이 아닙니다. 재개 후 다시 시도해주세요."
            );
        }

        if (status != InterviewSessionStatus.IN_PROGRESS) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_NOT_ACTIVE,
                    HttpStatus.CONFLICT,
                    "진행 가능한 면접 세션이 아닙니다. 재개 후 다시 시도해주세요."
            );
        }
    }

    private boolean shouldAutoPause(InterviewSession session) {
        Instant now = Instant.now();
        Instant lastActivityAt = interviewAnswerRepository.findTopBySessionIdOrderByCreatedAtDesc(session.getId())
                .map(InterviewAnswer::getCreatedAt)
                .orElse(session.getStartedAt());

        if (lastActivityAt == null) {
            return false;
        }

        return lastActivityAt.isBefore(now.minus(AUTO_PAUSE_THRESHOLD));
    }

    private long requirePositiveLong(Long value, String field) {
        if (value == null) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail(field, "required"))
            );
        }

        if (value <= 0L) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail(field, "invalid"))
            );
        }

        return value;
    }

    private int requirePositiveInt(Integer value, String field) {
        if (value == null) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail(field, "required"))
            );
        }

        if (value <= 0) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail(field, "invalid"))
            );
        }

        return value;
    }

    private String normalizeAnswerText(String answerText, boolean skipped) {
        if (skipped) {
            return null;
        }

        if (answerText == null || answerText.trim().isEmpty()) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_ANSWER_REQUIRED,
                    HttpStatus.BAD_REQUEST,
                    "답변을 입력해주세요.",
                    false,
                    List.of(new FieldErrorDetail("answerText", "required"))
            );
        }

        String normalized = answerText.trim();
        if (normalized.length() < 50) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_ANSWER_TOO_SHORT,
                    HttpStatus.BAD_REQUEST,
                    "답변은 50자 이상 입력해주세요.",
                    false,
                    List.of(new FieldErrorDetail("answerText", "min_50"))
            );
        }

        if (normalized.length() > 1000) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail("answerText", "max_1000"))
            );
        }

        return normalized;
    }
}
