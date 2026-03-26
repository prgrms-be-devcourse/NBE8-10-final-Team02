package com.back.backend.domain.interview.service;

import com.back.backend.domain.interview.dto.request.StartInterviewSessionRequest;
import com.back.backend.domain.interview.dto.response.InterviewResultResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionCompletionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionDetailResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionTransitionResponse;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewAnswerTag;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.mapper.InterviewResponseMapper;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.domain.interview.repository.InterviewAnswerRepository;
import com.back.backend.domain.interview.repository.InterviewAnswerTagRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.response.FieldErrorDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

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
    private final InterviewAnswerRepository interviewAnswerRepository;
    private final FeedbackTagRepository feedbackTagRepository;
    private final InterviewAnswerTagRepository interviewAnswerTagRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewResultGenerationService interviewResultGenerationService;
    private final InterviewResponseMapper interviewResponseMapper;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

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

        Instant now = clock.instant();
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

    @Transactional(readOnly = true)
    public List<InterviewSessionResponse> getSessions(long userId) {
        return interviewSessionRepository.findAllByUserIdOrderByStartedAtDesc(userId).stream()
                .sorted(Comparator
                        .comparing((InterviewSession session) -> isActiveStatus(session.getStatus()) ? 0 : 1)
                        .thenComparing(InterviewSession::getStartedAt, Comparator.reverseOrder()))
                .map(interviewResponseMapper::toInterviewSessionResponse)
                .toList();
    }

    @Transactional
    public InterviewSessionDetailResponse getSessionDetail(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        normalizeAutoPauseIfExpired(session);

        long totalQuestionCount = interviewQuestionRepository.countByQuestionSetId(session.getQuestionSet().getId());
        long answeredQuestionCount = interviewAnswerRepository.countBySessionId(session.getId());
        long remainingQuestionCount = Math.max(totalQuestionCount - answeredQuestionCount, 0);
        // 복원 화면의 현재 질문은 "이미 저장된 답변 수 + 1" 순번으로 고정한다.
        // 남은 질문이 없거나 종료 상태면 현재 질문을 노출하지 않는다.
        InterviewQuestion currentQuestion = resolveCurrentQuestion(session, answeredQuestionCount, remainingQuestionCount);

        return interviewResponseMapper.toInterviewSessionDetailResponse(
                session,
                currentQuestion,
                totalQuestionCount,
                answeredQuestionCount,
                remainingQuestionCount,
                session.getStatus() == InterviewSessionStatus.PAUSED
        );
    }

    @Transactional(readOnly = true)
    public InterviewResultResponse getSessionResult(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        validateResultReadable(session);

        List<InterviewAnswer> answers = interviewAnswerRepository
                .findAllWithQuestionBySessionIdOrderByAnswerOrderAsc(session.getId());
        if (answers.isEmpty()) {
            throw resultNotReady();
        }

        Map<Long, List<InterviewAnswerTag>> tagsByAnswerId = groupTagsByAnswerId(
                interviewAnswerTagRepository.findAllWithTagBySessionIdOrderByAnswerOrderAsc(session.getId())
        );
        validateReadableAnswers(answers, tagsByAnswerId);

        return interviewResponseMapper.toInterviewResultResponse(session, answers, tagsByAnswerId);
    }

    public InterviewSessionCompletionResponse completeSession(long userId, long sessionId) {
        SessionCompletionPreparation preparation = executeInTransaction(
                () -> prepareSessionCompletion(userId, sessionId)
        );
        InterviewResultGenerationService.GeneratedInterviewResult generatedResult =
                interviewResultGenerationService.generate(
                        preparation.sessionId(),
                        preparation.questionSetId(),
                        preparation.answers()
                );

        return executeInTransaction(
                () -> finalizeSessionCompletion(userId, preparation, generatedResult)
        );
    }

    @Transactional
    public InterviewSessionTransitionResponse pauseSession(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        validateNotCompleted(session);
        validatePauseAvailable(session);

        Instant updatedAt = clock.instant();
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

        Instant resumedAt = clock.instant();
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

        if (lastActivityAt.isBefore(clock.instant().minus(AUTO_PAUSE_THRESHOLD))) {
            session.changeStatus(InterviewSessionStatus.PAUSED);
            return true;
        }

        return false;
    }

    private SessionCompletionPreparation prepareSessionCompletion(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        validateCompleteAvailable(session);
        validateAllQuestionsAnswered(session);

        Instant endedAt = clock.instant();
        session.changeStatus(InterviewSessionStatus.COMPLETED);
        session.changeEndedAt(endedAt);

        // 결과 생성 payload는 외부 AI 호출 전에 모두 읽어두고 트랜잭션을 닫는다.
        // 이렇게 해야 세션 종료 기록은 남기면서도 외부 API를 긴 트랜잭션 안에 두지 않는다.
        List<InterviewAnswer> answers = interviewAnswerRepository
                .findAllWithQuestionBySessionIdOrderByAnswerOrderAsc(session.getId());
        return new SessionCompletionPreparation(
                session.getId(),
                session.getQuestionSet().getId(),
                endedAt,
                answers
        );
    }

    private InterviewSessionCompletionResponse finalizeSessionCompletion(
            long userId,
            SessionCompletionPreparation preparation,
            InterviewResultGenerationService.GeneratedInterviewResult generatedResult
    ) {
        InterviewSession session = getOwnedSession(userId, preparation.sessionId());
        applyGeneratedResult(session, generatedResult, preparation.endedAt());
        return interviewResponseMapper.toInterviewSessionCompletionResponse(session);
    }

    private void applyGeneratedResult(
            InterviewSession session,
            InterviewResultGenerationService.GeneratedInterviewResult generatedResult,
            Instant endedAt
    ) {
        session.applyResult(generatedResult.totalScore(), generatedResult.summaryFeedback());
        session.changeStatus(InterviewSessionStatus.FEEDBACK_COMPLETED);
        session.changeEndedAt(endedAt);

        Map<Integer, InterviewAnswer> answersByOrder = interviewAnswerRepository
                .findAllBySessionIdOrderByAnswerOrderAsc(session.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        InterviewAnswer::getAnswerOrder,
                        answer -> answer,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, FeedbackTag> tagsByName = loadTagsByName(generatedResult.answers());

        List<InterviewAnswerTag> answerTags = generatedResult.answers().stream()
                .peek(answerResult -> requireManagedAnswer(answersByOrder, answerResult.answerOrder())
                        .applyEvaluation(answerResult.score(), answerResult.evaluationRationale()))
                .flatMap(answerResult -> answerResult.tagNames().stream()
                        .map(tagName -> InterviewAnswerTag.builder()
                                .answer(requireManagedAnswer(answersByOrder, answerResult.answerOrder()))
                                .tag(requireFeedbackTag(tagsByName, tagName))
                                .build()))
                .toList();

        if (!answerTags.isEmpty()) {
            interviewAnswerTagRepository.saveAll(answerTags);
        }
    }

    private Map<String, FeedbackTag> loadTagsByName(
            List<InterviewResultGenerationService.GeneratedInterviewAnswerResult> generatedAnswers
    ) {
        Set<String> requestedTagNames = generatedAnswers.stream()
                .flatMap(answerResult -> answerResult.tagNames().stream())
                .collect(java.util.stream.Collectors.toSet());
        if (requestedTagNames.isEmpty()) {
            return Map.of();
        }

        return feedbackTagRepository.findAllByTagNameIn(requestedTagNames).stream()
                .collect(java.util.stream.Collectors.toMap(FeedbackTag::getTagName, tag -> tag));
    }

    private InterviewAnswer requireManagedAnswer(
            Map<Integer, InterviewAnswer> answersByOrder,
            int answerOrder
    ) {
        InterviewAnswer answer = answersByOrder.get(answerOrder);
        if (answer == null) {
            throw incompleteResult();
        }
        return answer;
    }

    private FeedbackTag requireFeedbackTag(Map<String, FeedbackTag> tagsByName, String tagName) {
        FeedbackTag tag = tagsByName.get(tagName);
        if (tag == null) {
            throw incompleteResult();
        }
        return tag;
    }

    private InterviewQuestion resolveCurrentQuestion(
            InterviewSession session,
            long answeredQuestionCount,
            long remainingQuestionCount
    ) {
        if (remainingQuestionCount <= 0 || isTerminalStatus(session.getStatus())) {
            return null;
        }

        int currentQuestionOrder = Math.toIntExact(answeredQuestionCount + 1);
        return interviewQuestionRepository.findByQuestionSetIdAndQuestionOrder(
                session.getQuestionSet().getId(),
                currentQuestionOrder
        ).orElse(null);
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
        if (isTerminalStatus(status)) {
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

    private void validateCompleteAvailable(InterviewSession session) {
        if (isTerminalStatus(session.getStatus())) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED,
                    HttpStatus.CONFLICT,
                    "이미 종료된 면접 세션입니다."
            );
        }

        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS
                && session.getStatus() != InterviewSessionStatus.PAUSED) {
            throw new ServiceException(
                    ErrorCode.INTERVIEW_SESSION_NOT_ACTIVE,
                    HttpStatus.CONFLICT,
                    "진행 가능한 면접 세션이 아닙니다. 재개 후 다시 시도해주세요."
            );
        }
    }

    private boolean isTerminalStatus(InterviewSessionStatus status) {
        return status == InterviewSessionStatus.COMPLETED || status == InterviewSessionStatus.FEEDBACK_COMPLETED;
    }

    private boolean isActiveStatus(InterviewSessionStatus status) {
        return ACTIVE_STATUSES.contains(status);
    }

    private ServiceException incompleteResult() {
        return new ServiceException(
                ErrorCode.INTERVIEW_RESULT_INCOMPLETE,
                HttpStatus.BAD_GATEWAY,
                "면접 결과 생성 결과가 완전하지 않습니다.",
                true
        );
    }

    private void validateResultReadable(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.FEEDBACK_COMPLETED) {
            throw resultNotReady();
        }

        if (session.getTotalScore() == null
                || session.getSummaryFeedback() == null
                || session.getSummaryFeedback().isBlank()) {
            throw resultNotReady();
        }
    }

    private void validateReadableAnswers(
            List<InterviewAnswer> answers,
            Map<Long, List<InterviewAnswerTag>> tagsByAnswerId
    ) {
        for (InterviewAnswer answer : answers) {
            if (answer.getScore() == null
                    || answer.getEvaluationRationale() == null
                    || answer.getEvaluationRationale().isBlank()) {
                throw resultNotReady();
            }

            if (tagsByAnswerId.getOrDefault(answer.getId(), List.of()).size() > 3) {
                throw resultNotReady();
            }
        }
    }

    private Map<Long, List<InterviewAnswerTag>> groupTagsByAnswerId(List<InterviewAnswerTag> answerTags) {
        Map<Long, List<InterviewAnswerTag>> tagsByAnswerId = new LinkedHashMap<>();
        for (InterviewAnswerTag answerTag : answerTags) {
            tagsByAnswerId.computeIfAbsent(answerTag.getAnswer().getId(), ignored -> new ArrayList<>())
                    .add(answerTag);
        }
        return tagsByAnswerId;
    }

    private ServiceException resultNotReady() {
        return new ServiceException(
                ErrorCode.INTERVIEW_RESULT_INCOMPLETE,
                HttpStatus.CONFLICT,
                "면접 결과가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.",
                true
        );
    }

    private void validateAllQuestionsAnswered(InterviewSession session) {
        long totalQuestionCount = interviewQuestionRepository.countByQuestionSetId(session.getQuestionSet().getId());
        long answeredQuestionCount = interviewAnswerRepository.countBySessionId(session.getId());
        long remainingQuestionCount = Math.max(totalQuestionCount - answeredQuestionCount, 0);

        if (remainingQuestionCount > 0) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "모든 질문에 답변한 뒤 세션을 종료해주세요.",
                    false,
                    List.of(new FieldErrorDetail("remainingQuestionCount", "incomplete"))
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

    private <T> T executeInTransaction(Supplier<T> action) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
    }

    private record SessionCompletionPreparation(
            long sessionId,
            long questionSetId,
            Instant endedAt,
            List<InterviewAnswer> answers
    ) {
    }
}
