package com.back.backend.domain.interview.service;

import com.back.backend.domain.interview.dto.request.SubmitInterviewAnswerRequest;
import com.back.backend.domain.interview.dto.response.InterviewAnswerSubmitResponse;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionQuestion;
import com.back.backend.domain.interview.mapper.InterviewResponseMapper;
import com.back.backend.domain.interview.repository.InterviewAnswerRepository;
import com.back.backend.domain.interview.repository.InterviewSessionQuestionRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.response.FieldErrorDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewAnswerService {

    private final InterviewSessionService interviewSessionService;
    private final InterviewSessionQuestionRepository interviewSessionQuestionRepository;
    private final InterviewAnswerRepository interviewAnswerRepository;
    private final InterviewResponseMapper interviewResponseMapper;
    private final Clock clock;

    // auto-pause 대상 세션은 충돌 오류를 반환하더라도 paused 상태 보정은 남겨야 한다.
    // 그래서 ServiceException이 나와도 세션 상태 변경까지는 롤백하지 않는다.
    @Transactional(noRollbackFor = ServiceException.class)
    public InterviewAnswerSubmitResponse submitAnswer(
            long userId,
            long sessionId,
            SubmitInterviewAnswerRequest request
    ) {
        InterviewSession session = interviewSessionService.getOwnedSession(userId, sessionId);
        interviewSessionService.validateAnswerableSession(session);

        long questionId = requirePositiveLong(request.questionId(), "questionId");
        int answerOrder = requirePositiveInt(request.answerOrder(), "answerOrder");
        // 답변은 현재 차례의 질문 하나만 순차 제출할 수 있다.
        // 이미 저장된 답변 수를 기준으로 다음 차례를 계산해 questionId와 answerOrder를 함께 검증한다.
        int expectedAnswerOrder = Math.toIntExact(interviewAnswerRepository.countBySessionId(sessionId) + 1L);

        InterviewSessionQuestion requestedQuestion = interviewSessionQuestionRepository.findByIdAndSessionId(
                        questionId,
                        session.getId()
                )
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "세션 또는 질문을 찾을 수 없습니다."
                ));

        InterviewSessionQuestion currentQuestion = interviewSessionQuestionRepository.findAllUnansweredBySessionIdOrderByQuestionOrderAsc(
                        sessionId,
                        org.springframework.data.domain.PageRequest.of(0, 1)
                ).stream()
                .findFirst()
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
        Instant submittedAt = clock.instant();
        session.changeLastActivityAt(submittedAt);

        InterviewAnswer savedAnswer = interviewAnswerRepository.save(
                InterviewAnswer.builder()
                        .session(session)
                        .sessionQuestion(requestedQuestion)
                        .answerOrder(expectedAnswerOrder)
                        .answerText(normalizedAnswerText)
                        .skipped(request.isSkipped())
                        .build()
        );

        return interviewResponseMapper.toInterviewAnswerSubmitResponse(savedAnswer);
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
            // 건너뛰기 응답은 open-items 기준상 50자 최소 길이 규칙의 예외다.
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
