package com.back.backend.domain.interview.service;

import com.back.backend.domain.interview.dto.request.StartInterviewSessionRequest;
import com.back.backend.domain.interview.dto.response.InterviewSessionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionTransitionResponse;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.mapper.InterviewResponseMapper;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
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
public class InterviewSessionService {

    private static final Duration AUTO_PAUSE_THRESHOLD = Duration.ofMinutes(30);
    // paused 세션도 사용자가 이어서 재개할 수 있는 활성 세션으로 본다.
    // 여기서 제외하면 새 세션을 하나 더 시작해 단일 활성 세션 규칙이 깨질 수 있다.
    private static final List<InterviewSessionStatus> ACTIVE_STATUSES = List.of(
            InterviewSessionStatus.IN_PROGRESS,
            InterviewSessionStatus.PAUSED
    );

    private final InterviewQuestionSetRepository interviewQuestionSetRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewResponseMapper interviewResponseMapper;

    @Transactional
    public InterviewSessionResponse startSession(long userId, StartInterviewSessionRequest request) {
        long questionSetId = requireQuestionSetId(request.questionSetId());
        validateNoActiveSession(userId);

        InterviewQuestionSet questionSet = interviewQuestionSetRepository.findByIdAndUserId(questionSetId, userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "질문 세트를 찾을 수 없습니다."
                ));

        validateQuestionCount(questionSetId);

        Instant now = Instant.now();
        InterviewSession session = interviewSessionRepository.save(
                InterviewSession.builder()
                        .user(questionSet.getUser())
                        .questionSet(questionSet)
                        .status(InterviewSessionStatus.IN_PROGRESS)
                        .startedAt(now)
                        .lastActivityAt(now)
                        .endedAt(null)
                        .build()
        );

        return interviewResponseMapper.toInterviewSessionResponse(session);
    }

    @Transactional
    public InterviewSessionTransitionResponse pauseSession(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        validateNotCompleted(session);
        validatePauseAvailable(session);

        Instant updatedAt = Instant.now();
        session.changeStatus(InterviewSessionStatus.PAUSED);
        return interviewResponseMapper.toInterviewSessionTransitionResponse(session, updatedAt);
    }

    @Transactional
    public InterviewSessionTransitionResponse resumeSession(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        validateNotCompleted(session);
        // 오래 방치된 in_progress 세션은 먼저 paused로 정규화한 뒤 resume 경로를 탄다.
        // 사용자는 별도 pause 호출 없이 resume 하나로 만료된 세션을 다시 이어갈 수 있다.
        normalizeAutoPauseIfExpired(session);

        if (session.getStatus() != InterviewSessionStatus.PAUSED) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_STATUS_CONFLICT,
                    HttpStatus.CONFLICT,
                    "현재 상태에서는 세션 상태를 변경할 수 없습니다."
            );
        }

        Instant resumedAt = Instant.now();
        session.changeStatus(InterviewSessionStatus.IN_PROGRESS);
        session.changeLastActivityAt(resumedAt);
        return interviewResponseMapper.toInterviewSessionTransitionResponse(session, resumedAt);
    }

    public InterviewSession getOwnedSession(long userId, long sessionId) {
        return interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "세션을 찾을 수 없습니다."
                ));
    }

    public void validateAnswerableSession(InterviewSession session) {
        validateNotCompleted(session);

        if (normalizeAutoPauseIfExpired(session)) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_NOT_ACTIVE,
                    HttpStatus.CONFLICT,
                    "진행 가능한 면접 세션이 아닙니다. 재개 후 다시 시도해주세요."
            );
        }

        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_NOT_ACTIVE,
                    HttpStatus.CONFLICT,
                    "진행 가능한 면접 세션이 아닙니다. 재개 후 다시 시도해주세요."
            );
        }
    }

    public boolean normalizeAutoPauseIfExpired(InterviewSession session) {
        // 답변 제출과 resume 진입점에서 같은 무응답 정책을 적용하려고 읽는 순간 상태를 보정한다.
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            return false;
        }

        Instant lastActivityAt = session.getLastActivityAt();
        if (lastActivityAt == null) {
            return false;
        }

        if (lastActivityAt.isBefore(Instant.now().minus(AUTO_PAUSE_THRESHOLD))) {
            session.changeStatus(InterviewSessionStatus.PAUSED);
            return true;
        }

        return false;
    }

    private long requireQuestionSetId(Long questionSetId) {
        if (questionSetId == null) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail("questionSetId", "required"))
            );
        }

        if (questionSetId <= 0) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail("questionSetId", "invalid"))
            );
        }

        return questionSetId;
    }

    private void validateNoActiveSession(long userId) {
        if (interviewSessionRepository.existsByUserIdAndStatusIn(userId, ACTIVE_STATUSES)) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_ALREADY_ACTIVE,
                    HttpStatus.CONFLICT,
                    "이미 활성 면접 세션이 있습니다."
            );
        }
    }

    private void validateNotCompleted(InterviewSession session) {
        InterviewSessionStatus status = session.getStatus();
        if (status == InterviewSessionStatus.COMPLETED || status == InterviewSessionStatus.FEEDBACK_COMPLETED) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED,
                    HttpStatus.CONFLICT,
                    "이미 종료된 면접 세션입니다."
            );
        }
    }

    private void validatePauseAvailable(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_STATUS_CONFLICT,
                    HttpStatus.CONFLICT,
                    "현재 상태에서는 세션 상태를 변경할 수 없습니다."
            );
        }
    }

    private void validateQuestionCount(long questionSetId) {
        long questionCount = interviewQuestionRepository.countByQuestionSetId(questionSetId);
        if (questionCount < 3 || questionCount > 20) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "면접 세션은 3개 이상 20개 이하 질문으로만 시작할 수 있습니다.",
                    false,
                    List.of(new FieldErrorDetail("questionCount", "out_of_range"))
            );
        }
    }
}
