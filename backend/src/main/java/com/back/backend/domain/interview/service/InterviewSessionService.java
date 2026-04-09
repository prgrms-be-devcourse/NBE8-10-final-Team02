package com.back.backend.domain.interview.service;

import com.back.backend.domain.followup.config.FollowupRulesProperties;
import com.back.backend.domain.followup.dto.response.FollowupAnalyzeResponse;
import com.back.backend.domain.followup.model.CandidateQuestionType;
import com.back.backend.domain.followup.model.FinalAction;
import com.back.backend.domain.followup.model.QuestionType;
import com.back.backend.domain.followup.service.FollowupRuleService;
import com.back.backend.domain.interview.dto.request.StartInterviewSessionRequest;
import com.back.backend.domain.interview.dto.response.InterviewSessionCompletionFollowupContextResponse;
import com.back.backend.domain.interview.dto.response.InterviewResultResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionCompletionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionDetailResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionResponse;
import com.back.backend.domain.interview.dto.response.InterviewSessionTransitionResponse;
import com.back.backend.domain.interview.entity.FeedbackTag;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewAnswerTag;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionQuestion;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.mapper.InterviewResponseMapper;
import com.back.backend.domain.interview.repository.FeedbackTagRepository;
import com.back.backend.domain.interview.repository.InterviewAnswerRepository;
import com.back.backend.domain.interview.repository.InterviewAnswerTagRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.domain.interview.repository.InterviewSessionQuestionRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.response.FieldErrorDetail;
import com.back.backend.domain.activity.event.InterviewSessionCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private static final Duration AUTO_PAUSE_THRESHOLD = Duration.ofMinutes(30);
    private static final Duration RESULT_GENERATION_RETRY_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_FOLLOWUP_DEPTH = 1;
    private static final int QUESTION_ORDER_SHIFT_OFFSET = 1000;
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
    private final InterviewSessionQuestionRepository interviewSessionQuestionRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final FollowupRuleService followupRuleService;
    private final FollowupRulesProperties followupRulesProperties;
    private final CandidateFollowupQuestionFactory candidateFollowupQuestionFactory;
    private final InterviewFollowupGenerationService interviewFollowupGenerationService;
    private final InterviewCompletionFollowupGenerationService interviewCompletionFollowupGenerationService;
    private final InterviewResultGenerationService interviewResultGenerationService;
    private final InterviewResponseMapper interviewResponseMapper;
    private final AsyncInterviewResultGenerator asyncInterviewResultGenerator;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationEventPublisher eventPublisher;
    private final Set<Long> followupGenerationInFlight = ConcurrentHashMap.newKeySet();
    private final Set<Long> completionFollowupReviewInFlight = ConcurrentHashMap.newKeySet();
    private final Set<Long> resultGenerationInFlight = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> deferredResultRetryAt = new ConcurrentHashMap<>();

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
        createSessionQuestionSnapshot(session);

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

    public InterviewSessionDetailResponse getSessionDetail(long userId, long sessionId) {
        resolvePendingFollowupIfNeeded(userId, sessionId);
        return executeInTransaction(() -> loadSessionDetailResponse(userId, sessionId));
    }

    private InterviewSessionDetailResponse loadSessionDetailResponse(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        normalizeAutoPauseIfExpired(session);

        List<InterviewAnswer> answeredAnswers = interviewAnswerRepository
                .findAllWithSessionQuestionBySessionIdOrderByAnswerOrderAsc(session.getId());
        long totalQuestionCount = interviewSessionQuestionRepository.countBySessionId(session.getId());
        long answeredQuestionCount = answeredAnswers.size();
        long remainingQuestionCount = Math.max(totalQuestionCount - answeredQuestionCount, 0);
        InterviewSessionQuestion currentQuestion = resolveCurrentQuestion(session, answeredQuestionCount, remainingQuestionCount);
        InterviewSessionCompletionFollowupContextResponse completionFollowupContext =
                buildCompletionFollowupContext(session, currentQuestion);
        var transcriptEntries = answeredAnswers.stream()
                .map(interviewResponseMapper::toInterviewSessionTranscriptEntryResponse)
                .toList();

        return interviewResponseMapper.toInterviewSessionDetailResponse(
                session,
                currentQuestion,
                completionFollowupContext,
                transcriptEntries,
                totalQuestionCount,
                answeredQuestionCount,
                remainingQuestionCount,
                session.getStatus() == InterviewSessionStatus.PAUSED
        );
    }

    public InterviewResultResponse getSessionResult(long userId, long sessionId) {
        retryPendingResultGenerationIfNeeded(userId, sessionId);
        return executeReadOnlyInTransaction(() -> loadSessionResultResponse(userId, sessionId));
    }

    private InterviewResultResponse loadSessionResultResponse(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        validateResultReadable(session);

        List<InterviewAnswer> answers = interviewAnswerRepository
                .findAllWithSessionQuestionBySessionIdOrderByAnswerOrderAsc(session.getId());
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
        CompletionFollowupReviewPreparation completionReviewPreparation = executeInTransaction(
                () -> prepareCompletionFollowupReview(userId, sessionId)
        );
        CompletionFollowupReviewOutcome completionReviewOutcome = resolveCompletionFollowupReview(completionReviewPreparation);
        if (completionReviewOutcome.followupInserted()) {
            throw remainingQuestionsIncomplete();
        }

        SessionCompletionPreparation preparation = executeInTransaction(
                () -> prepareSessionCompletion(userId, sessionId)
        );
        beginResultGenerationAttempt(preparation.sessionId());

        // AI 결과 생성을 비동기로 제출 — HTTP 응답을 즉시 반환
        // 결과는 GET /result 폴링 시 retryPendingResultGenerationIfNeeded()로 확인/재시도
        asyncInterviewResultGenerator.submitAsync(
                userId,
                preparation.sessionId(),
                preparation.questionSetId(),
                preparation.answers(),
                preparation.jobRole(),
                generatedResult -> {
                    executeInTransaction(
                            () -> finalizeSessionCompletion(userId, preparation, generatedResult)
                    );
                    clearResultGenerationRetryState(preparation.sessionId());
                },
                new AsyncInterviewResultGenerator.ResultGenerationFailureHandler() {
                    @Override
                    public void onFailure(long sid, ServiceException exception) {
                        handleResultGenerationFailure(sid, exception);
                    }

                    @Override
                    public void onUnexpectedFailure(long sid) {
                        handleResultGenerationFailure(sid, unexpectedResultGenerationFailure());
                    }
                }
        );

        // 세션 상태(COMPLETED)와 endedAt만 포함된 응답을 즉시 반환
        // totalScore, summaryFeedback은 null (AI 결과 생성 완료 전)
        return new InterviewSessionCompletionResponse(
                preparation.sessionId(),
                InterviewSessionStatus.COMPLETED.getValue(),
                null,
                null,
                preparation.endedAt()
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

    private CompletionFollowupReviewPreparation prepareCompletionFollowupReview(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        validateCompleteAvailable(session);
        validateAllQuestionsAnswered(session);

        if (session.getCompletionFollowupReviewedAt() != null) {
            return CompletionFollowupReviewPreparation.reviewSkipped(session.getId());
        }

        List<InterviewAnswer> answers = interviewAnswerRepository
                .findAllWithSessionQuestionBySessionIdOrderByAnswerOrderAsc(session.getId());
        List<AnsweredQuestionThread> answeredThreads = buildAnsweredQuestionThreads(answers);
        if (answeredThreads.isEmpty()) {
            session.markCompletionFollowupReviewed(clock.instant());
            return CompletionFollowupReviewPreparation.reviewSkipped(session.getId());
        }

        return CompletionFollowupReviewPreparation.reviewPending(
                session.getId(),
                buildCompletionFollowupGenerationRequest(session, answeredThreads)
        );
    }

    private CompletionFollowupReviewOutcome resolveCompletionFollowupReview(
            CompletionFollowupReviewPreparation completionReviewPreparation
    ) {
        if (completionReviewPreparation == null || !completionReviewPreparation.reviewNeeded()) {
            return CompletionFollowupReviewOutcome.notInserted();
        }

        if (!completionFollowupReviewInFlight.add(completionReviewPreparation.sessionId())) {
            return CompletionFollowupReviewOutcome.notInserted();
        }

        List<InterviewCompletionFollowupGenerationService.CompletionFollowupDecision> completionDecisions;
        try {
            completionDecisions = interviewCompletionFollowupGenerationService.generate(completionReviewPreparation.request());
        } catch (ServiceException exception) {
            completionDecisions = List.of();
        } catch (RuntimeException exception) {
            completionDecisions = List.of();
        }

        try {
            List<InterviewCompletionFollowupGenerationService.CompletionFollowupDecision> finalCompletionDecisions =
                    completionDecisions;
            return executeInTransaction(
                    () -> finalizeCompletionFollowupReview(completionReviewPreparation, finalCompletionDecisions)
            );
        } finally {
            completionFollowupReviewInFlight.remove(completionReviewPreparation.sessionId());
        }
    }

    private CompletionFollowupReviewOutcome finalizeCompletionFollowupReview(
            CompletionFollowupReviewPreparation completionReviewPreparation,
            List<InterviewCompletionFollowupGenerationService.CompletionFollowupDecision> completionDecisions
    ) {
        InterviewSession session = interviewSessionRepository.findById(completionReviewPreparation.sessionId())
                .orElse(null);
        if (session == null) {
            return CompletionFollowupReviewOutcome.notInserted();
        }

        if (session.getCompletionFollowupReviewedAt() != null) {
            return CompletionFollowupReviewOutcome.notInserted();
        }

        Instant reviewedAt = clock.instant();
        boolean inserted = false;
        for (InterviewCompletionFollowupGenerationService.CompletionFollowupDecision completionDecision : completionDecisions
                .stream()
                .sorted(Comparator.comparingInt(
                        InterviewCompletionFollowupGenerationService.CompletionFollowupDecision::parentQuestionOrder
                ).reversed())
                .toList()) {
            InterviewSessionQuestion parentQuestion = interviewSessionQuestionRepository.findBySessionIdAndQuestionOrder(
                    session.getId(),
                    completionDecision.parentQuestionOrder()
            ).orElse(null);
            if (parentQuestion == null) {
                continue;
            }
            if (interviewSessionQuestionRepository.existsBySessionIdAndParentSessionQuestionId(
                    session.getId(),
                    parentQuestion.getId()
            )) {
                continue;
            }

            insertFollowupQuestionDraft(parentQuestion, completionDecision.followupDraft());
            inserted = true;
        }

        InterviewSession managedSession = interviewSessionRepository.findById(session.getId()).orElse(null);
        if (managedSession == null) {
            return CompletionFollowupReviewOutcome.notInserted();
        }
        managedSession.markCompletionFollowupReviewed(reviewedAt);
        return inserted ? CompletionFollowupReviewOutcome.inserted() : CompletionFollowupReviewOutcome.notInserted();
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
                .findAllWithSessionQuestionBySessionIdOrderByAnswerOrderAsc(session.getId());
        String jobRole = session.getQuestionSet().getApplication().getJobRole();
        return new SessionCompletionPreparation(
                session.getId(),
                session.getQuestionSet().getId(),
                endedAt,
                answers,
                jobRole
        );
    }

    private InterviewSessionCompletionResponse finalizeSessionCompletion(
            long userId,
            SessionCompletionPreparation preparation,
            InterviewResultGenerationService.GeneratedInterviewResult generatedResult
    ) {
        InterviewSession session = finalizeGeneratedResult(userId, preparation, generatedResult);
        return interviewResponseMapper.toInterviewSessionCompletionResponse(session);
    }

    private void retryPendingResultGenerationIfNeeded(long userId, long sessionId) {
        SessionCompletionPreparation preparation = executeNullableInTransaction(
                () -> preparePendingResultGeneration(userId, sessionId)
        );
        if (preparation == null) {
            return;
        }

        if (!tryBeginDeferredResultGeneration(preparation.sessionId())) {
            return;
        }

        try {
            InterviewResultGenerationService.GeneratedInterviewResult generatedResult =
                    interviewResultGenerationService.generate(
                            userId,
                            preparation.sessionId(),
                            preparation.questionSetId(),
                            preparation.answers(),
                            preparation.jobRole()
                    );
            executeInTransaction(() -> finalizeGeneratedResult(userId, preparation, generatedResult));
            clearResultGenerationRetryState(preparation.sessionId());
        } catch (ServiceException exception) {
            handleResultGenerationFailure(preparation.sessionId(), exception);
            if (exception.isRetryable()) {
                return;
            }
            throw exception;
        } catch (RuntimeException exception) {
            ServiceException generationFailure = unexpectedResultGenerationFailure();
            handleResultGenerationFailure(preparation.sessionId(), generationFailure);
            return;
        }
    }

    private SessionCompletionPreparation preparePendingResultGeneration(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            clearResultGenerationRetryState(session.getId());
            return null;
        }

        List<InterviewAnswer> answers = interviewAnswerRepository
                .findAllWithSessionQuestionBySessionIdOrderByAnswerOrderAsc(session.getId());
        if (answers.isEmpty()) {
            return null;
        }

        Instant endedAt = session.getEndedAt() == null ? clock.instant() : session.getEndedAt();
        String jobRole = session.getQuestionSet().getApplication().getJobRole();
        return new SessionCompletionPreparation(
                session.getId(),
                session.getQuestionSet().getId(),
                endedAt,
                answers,
                jobRole
        );
    }

    private boolean tryBeginDeferredResultGeneration(long sessionId) {
        Instant deferredAt = deferredResultRetryAt.get(sessionId);
        Instant now = clock.instant();
        if (deferredAt != null && deferredAt.isAfter(now)) {
            return false;
        }
        return resultGenerationInFlight.add(sessionId);
    }

    private void beginResultGenerationAttempt(long sessionId) {
        resultGenerationInFlight.add(sessionId);
    }

    private void handleResultGenerationFailure(long sessionId, ServiceException exception) {
        if (exception.isRetryable()) {
            deferredResultRetryAt.put(sessionId, clock.instant().plus(RESULT_GENERATION_RETRY_COOLDOWN));
            finishResultGenerationAttempt(sessionId);
            return;
        }
        finishResultGenerationAttempt(sessionId);
    }

    private void clearResultGenerationRetryState(long sessionId) {
        deferredResultRetryAt.remove(sessionId);
        finishResultGenerationAttempt(sessionId);
    }

    private ServiceException unexpectedResultGenerationFailure() {
        return new ServiceException(
                ErrorCode.INTERVIEW_RESULT_GENERATION_FAILED,
                HttpStatus.BAD_GATEWAY,
                "면접 결과 생성 중 오류가 발생했습니다.",
                true
        );
    }

    private void finishResultGenerationAttempt(long sessionId) {
        resultGenerationInFlight.remove(sessionId);
    }

    private InterviewSession finalizeGeneratedResult(
            long userId,
            SessionCompletionPreparation preparation,
            InterviewResultGenerationService.GeneratedInterviewResult generatedResult
    ) {
        InterviewSession session = getOwnedSession(userId, preparation.sessionId());
        if (session.getStatus() == InterviewSessionStatus.FEEDBACK_COMPLETED) {
            return session;
        }

        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            return session;
        }

        applyGeneratedResult(session, generatedResult, preparation.endedAt());
        return session;
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

        eventPublisher.publishEvent(
                new InterviewSessionCompletedEvent(session.getUser().getId(), session.getId()));
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

    private InterviewSessionQuestion resolveCurrentQuestion(
            InterviewSession session,
            long answeredQuestionCount,
            long remainingQuestionCount
    ) {
        if (remainingQuestionCount <= 0 || isTerminalStatus(session.getStatus())) {
            return null;
        }

        return interviewSessionQuestionRepository.findAllUnansweredBySessionIdOrderByQuestionOrderAsc(
                        session.getId(),
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .orElse(null);
    }

    private InterviewSessionCompletionFollowupContextResponse buildCompletionFollowupContext(
            InterviewSession session,
            InterviewSessionQuestion currentQuestion
    ) {
        if (!isCurrentCompletionFollowup(session, currentQuestion)) {
            return null;
        }

        InterviewSessionQuestion parentQuestion = currentQuestion.getParentSessionQuestion();
        if (parentQuestion == null) {
            return null;
        }

        InterviewSessionQuestion runtimeFollowupQuestion =
                parentQuestion.getQuestionType() == InterviewQuestionType.FOLLOW_UP ? parentQuestion : null;
        InterviewSessionQuestion rootQuestion = runtimeFollowupQuestion == null
                ? parentQuestion
                : runtimeFollowupQuestion.getParentSessionQuestion();

        if (rootQuestion == null || rootQuestion.getQuestionType() == InterviewQuestionType.FOLLOW_UP) {
            return null;
        }

        Map<Long, InterviewAnswer> answerByQuestionId = interviewAnswerRepository
                .findAllWithSessionQuestionBySessionIdOrderByAnswerOrderAsc(session.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        answer -> answer.getSessionQuestion().getId(),
                        answer -> answer
                ));

        InterviewAnswer rootAnswer = answerByQuestionId.get(rootQuestion.getId());
        if (rootAnswer == null) {
            return null;
        }

        InterviewAnswer runtimeFollowupAnswer = runtimeFollowupQuestion == null
                ? null
                : answerByQuestionId.get(runtimeFollowupQuestion.getId());
        if (runtimeFollowupQuestion != null && runtimeFollowupAnswer == null) {
            return null;
        }

        return interviewResponseMapper.toInterviewSessionCompletionFollowupContextResponse(
                rootQuestion,
                rootAnswer,
                runtimeFollowupQuestion,
                runtimeFollowupAnswer,
                currentQuestion,
                parentQuestion.getQuestionOrder()
        );
    }

    private boolean isCurrentCompletionFollowup(
            InterviewSession session,
            InterviewSessionQuestion currentQuestion
    ) {
        return currentQuestion != null
                && currentQuestion.getQuestionType() == InterviewQuestionType.FOLLOW_UP
                && session.getCompletionFollowupReviewedAt() != null;
    }

    private void resolvePendingFollowupIfNeeded(long userId, long sessionId) {
        PendingFollowupResolution pendingFollowup = executeNullableInTransaction(
                () -> preparePendingFollowupResolution(userId, sessionId)
        );
        if (pendingFollowup == null) {
            return;
        }

        if (pendingFollowup.followupDraft() != null) {
            executeWithoutResultInTransaction(
                    () -> finalizePendingFollowup(sessionId, pendingFollowup, pendingFollowup.followupDraft())
            );
            return;
        }

        if (pendingFollowup.aiRequest() == null) {
            executeWithoutResultInTransaction(() -> markFollowupResolved(sessionId, pendingFollowup.answerId()));
            return;
        }

        if (!followupGenerationInFlight.add(pendingFollowup.answerId())) {
            return;
        }

        try {
            FollowupQuestionDraft followupDraft =
                    interviewFollowupGenerationService.generate(pendingFollowup.aiRequest());
            executeWithoutResultInTransaction(
                    () -> finalizePendingFollowup(sessionId, pendingFollowup, followupDraft)
            );
        } catch (ServiceException exception) {
            executeWithoutResultInTransaction(() -> markFollowupResolved(sessionId, pendingFollowup.answerId()));
        } catch (RuntimeException exception) {
            executeWithoutResultInTransaction(() -> markFollowupResolved(sessionId, pendingFollowup.answerId()));
        } finally {
            followupGenerationInFlight.remove(pendingFollowup.answerId());
        }
    }

    private PendingFollowupResolution preparePendingFollowupResolution(long userId, long sessionId) {
        InterviewSession session = getOwnedSession(userId, sessionId);
        normalizeAutoPauseIfExpired(session);
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            return null;
        }

        InterviewAnswer answer = interviewAnswerRepository.findAllPendingFollowupCandidatesBySessionId(
                        sessionId,
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .orElse(null);
        if (answer == null) {
            return null;
        }

        InterviewSessionQuestion parentQuestion = answer.getSessionQuestion();
        int followUpDepth = calculateFollowupDepth(parentQuestion);
        boolean childAlreadyExists = interviewSessionQuestionRepository.existsBySessionIdAndParentSessionQuestionId(
                sessionId,
                parentQuestion.getId()
        );
        FollowupAnalyzeResponse analysis = analyzeFollowup(parentQuestion, answer);
        if (analysis == null) {
            return PendingFollowupResolution.resolveOnly(
                    answer.getId(),
                    parentQuestion.getId(),
                    FinalAction.NO_FOLLOW_UP
            );
        }

        boolean runtimeFollowupEligible = !(childAlreadyExists
                || parentQuestion.getQuestionType() == InterviewQuestionType.FOLLOW_UP
                || answer.isSkipped()
                || followUpDepth >= MAX_FOLLOWUP_DEPTH);
        if (!runtimeFollowupEligible || analysis.finalAction() == FinalAction.NO_FOLLOW_UP) {
            return PendingFollowupResolution.resolveOnly(
                    answer.getId(),
                    parentQuestion.getId(),
                    analysis.finalAction()
            );
        }

        if (analysis.finalAction() == FinalAction.USE_CANDIDATE) {
            CandidateQuestionType firstCandidateQuestionType = analysis.candidateQuestionTypes().stream()
                    .findFirst()
                    .orElse(null);
            return PendingFollowupResolution.withLocalDraft(
                    answer.getId(),
                    parentQuestion.getId(),
                    analysis.finalAction(),
                    candidateFollowupQuestionFactory.create(firstCandidateQuestionType)
            );
        }

        if (analysis.finalAction() == FinalAction.USE_DYNAMIC && !hasRuntimeDynamicBudgetBeenUsed(sessionId)) {
            return PendingFollowupResolution.withAiRequest(
                    answer.getId(),
                    parentQuestion.getId(),
                    analysis.finalAction(),
                    buildFollowupGenerationRequest(session, parentQuestion, answer, followUpDepth)
            );
        }

        return PendingFollowupResolution.resolveOnly(
                answer.getId(),
                parentQuestion.getId(),
                analysis.finalAction()
        );
    }

    private void finalizePendingFollowup(
            long sessionId,
            PendingFollowupResolution pendingFollowup,
            FollowupQuestionDraft followupDraft
    ) {
        InterviewAnswer answer = interviewAnswerRepository.findByIdAndSessionId(
                        pendingFollowup.answerId(),
                        sessionId
                )
                .orElse(null);
        if (answer == null || answer.getFollowupResolvedAt() != null) {
            return;
        }

        if (followupDraft != null) {
            InterviewSessionQuestion parentQuestion = interviewSessionQuestionRepository.findByIdAndSessionId(
                            pendingFollowup.parentSessionQuestionId(),
                            sessionId
                    )
                    .orElse(null);
            if (parentQuestion != null
                    && !interviewSessionQuestionRepository.existsBySessionIdAndParentSessionQuestionId(
                            sessionId,
                            parentQuestion.getId()
                    )) {
                insertFollowupQuestionDraft(parentQuestion, followupDraft);
            }
        }

        markFollowupResolved(sessionId, pendingFollowup.answerId());
    }

    private void insertFollowupQuestionDraft(
            InterviewSessionQuestion parentQuestion,
            FollowupQuestionDraft followupDraft
    ) {
        int parentQuestionOrder = parentQuestion.getQuestionOrder();
        interviewSessionQuestionRepository.addQuestionOrderOffsetAfter(
                parentQuestion.getSession().getId(),
                parentQuestionOrder,
                QUESTION_ORDER_SHIFT_OFFSET
        );
        interviewSessionQuestionRepository.subtractQuestionOrderOffsetFrom(
                parentQuestion.getSession().getId(),
                parentQuestionOrder + QUESTION_ORDER_SHIFT_OFFSET + 1,
                QUESTION_ORDER_SHIFT_OFFSET - 1
        );

        interviewSessionQuestionRepository.save(
                InterviewSessionQuestion.builder()
                        .session(parentQuestion.getSession())
                        .sourceQuestion(null)
                        .parentSessionQuestion(parentQuestion)
                        .questionOrder(parentQuestionOrder + 1)
                        .questionType(followupDraft.questionType())
                        .difficultyLevel(followupDraft.difficultyLevel())
                        .questionText(followupDraft.questionText())
                        .build()
        );
    }

    private void markFollowupResolved(long sessionId, long answerId) {
        interviewAnswerRepository.findByIdAndSessionId(answerId, sessionId)
                .filter(answer -> answer.getFollowupResolvedAt() == null)
                .ifPresent(answer -> answer.markFollowupResolved(clock.instant()));
    }

    private int calculateFollowupDepth(InterviewSessionQuestion sessionQuestion) {
        int depth = 0;
        InterviewSessionQuestion current = sessionQuestion;
        while (current.getParentSessionQuestion() != null) {
            depth++;
            current = current.getParentSessionQuestion();
        }
        return depth;
    }

    private List<AnsweredQuestionThread> buildAnsweredQuestionThreads(List<InterviewAnswer> answers) {
        Map<Long, InterviewAnswer> runtimeFollowupAnswerByRootQuestionId = new LinkedHashMap<>();
        for (InterviewAnswer answer : answers) {
            InterviewSessionQuestion sessionQuestion = answer.getSessionQuestion();
            if (sessionQuestion.getQuestionType() != InterviewQuestionType.FOLLOW_UP
                    || sessionQuestion.getParentSessionQuestion() == null) {
                continue;
            }

            InterviewSessionQuestion rootQuestion = findRootSessionQuestion(sessionQuestion);
            if (rootQuestion.getQuestionType() == InterviewQuestionType.FOLLOW_UP) {
                continue;
            }
            runtimeFollowupAnswerByRootQuestionId.put(rootQuestion.getId(), answer);
        }

        List<AnsweredQuestionThread> answeredThreads = new ArrayList<>();
        for (InterviewAnswer answer : answers) {
            InterviewSessionQuestion rootQuestion = answer.getSessionQuestion();
            if (rootQuestion.getQuestionType() == InterviewQuestionType.FOLLOW_UP
                    || answer.isSkipped()
                    || answer.getAnswerText() == null
                    || answer.getAnswerText().isBlank()) {
                continue;
            }

            InterviewAnswer runtimeFollowupAnswer = runtimeFollowupAnswerByRootQuestionId.get(rootQuestion.getId());
            InterviewSessionQuestion runtimeFollowupQuestion =
                    runtimeFollowupAnswer != null ? runtimeFollowupAnswer.getSessionQuestion() : null;
            answeredThreads.add(new AnsweredQuestionThread(
                    rootQuestion,
                    answer,
                    analyzeFollowup(rootQuestion, answer),
                    runtimeFollowupQuestion,
                    runtimeFollowupAnswer
            ));
        }
        return answeredThreads;
    }

    private InterviewCompletionFollowupGenerationService.CompletionFollowupGenerationRequest
    buildCompletionFollowupGenerationRequest(
            InterviewSession session,
            List<AnsweredQuestionThread> answeredThreads
    ) {
        return new InterviewCompletionFollowupGenerationService.CompletionFollowupGenerationRequest(
                session.getQuestionSet().getApplication().getJobRole(),
                session.getQuestionSet().getApplication().getCompanyName(),
                answeredThreads.stream()
                        .map(this::toCompletionAnsweredThread)
                        .toList()
        );
    }

    private com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder.AnsweredThread
    toCompletionAnsweredThread(AnsweredQuestionThread answeredThread) {
        return new com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder.AnsweredThread(
                answeredThread.tailQuestionOrder(),
                new com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder.ThreadQuestion(
                        answeredThread.rootQuestion().getQuestionOrder(),
                        answeredThread.rootQuestion().getQuestionType().getValue(),
                        answeredThread.rootQuestion().getQuestionText(),
                        answeredThread.rootQuestion().getDifficultyLevel().getValue()
                ),
                new com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder.ThreadAnswer(
                        answeredThread.rootAnswer().getAnswerText(),
                        answeredThread.rootAnswer().isSkipped()
                ),
                toCompletionRuntimeRuleSummary(answeredThread.runtimeRuleSummary()),
                answeredThread.runtimeFollowupQuestion() == null
                        ? null
                        : new com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder.ThreadQuestion(
                                answeredThread.runtimeFollowupQuestion().getQuestionOrder(),
                                answeredThread.runtimeFollowupQuestion().getQuestionType().getValue(),
                                answeredThread.runtimeFollowupQuestion().getQuestionText(),
                                answeredThread.runtimeFollowupQuestion().getDifficultyLevel().getValue()
                        ),
                answeredThread.runtimeFollowupAnswer() == null
                        ? null
                        : new com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder.ThreadAnswer(
                                answeredThread.runtimeFollowupAnswer().getAnswerText(),
                                answeredThread.runtimeFollowupAnswer().isSkipped()
                        )
        );
    }

    private com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder.RuntimeRuleSummary
    toCompletionRuntimeRuleSummary(FollowupAnalyzeResponse analysis) {
        if (analysis == null) {
            return null;
        }

        return new com.back.backend.domain.ai.pipeline.payload.InterviewCompletionFollowupPayloadBuilder.RuntimeRuleSummary(
                analysis.finalAction().name(),
                analysis.primaryGap() != null ? analysis.primaryGap().name() : null,
                analysis.secondaryGap() != null ? analysis.secondaryGap().name() : null,
                analysis.candidateQuestionTypes().stream()
                        .map(Enum::name)
                        .toList()
        );
    }

    private InterviewSessionQuestion findRootSessionQuestion(InterviewSessionQuestion sessionQuestion) {
        InterviewSessionQuestion current = sessionQuestion;
        while (current.getParentSessionQuestion() != null) {
            current = current.getParentSessionQuestion();
        }
        return current;
    }

    private FollowupAnalyzeResponse analyzeFollowup(InterviewSessionQuestion sessionQuestion, InterviewAnswer answer) {
        if (answer.getAnswerText() == null || answer.getAnswerText().isBlank()) {
            return null;
        }

        QuestionType followupQuestionType = resolveFollowupQuestionType(sessionQuestion);
        if (followupQuestionType == null) {
            return null;
        }

        return followupRuleService.analyze(followupQuestionType, answer.getAnswerText());
    }

    private QuestionType resolveFollowupQuestionType(InterviewSessionQuestion sessionQuestion) {
        InterviewQuestionType questionType = sessionQuestion.getQuestionType();
        if (questionType == InterviewQuestionType.PROJECT) {
            return QuestionType.PROJECT;
        }
        if (questionType == InterviewQuestionType.TECHNICAL_STACK) {
            return QuestionType.TECH;
        }
        if (questionType == InterviewQuestionType.FOLLOW_UP || questionType == InterviewQuestionType.TECHNICAL_CS) {
            return null;
        }

        String normalizedQuestionText = normalizeQuestionText(sessionQuestion.getQuestionText());
        if (questionType == InterviewQuestionType.EXPERIENCE || questionType == InterviewQuestionType.BEHAVIORAL) {
            if (containsQuestionTypeKeyword(normalizedQuestionText, QuestionType.PROBLEM)) {
                return QuestionType.PROBLEM;
            }
            if (containsQuestionTypeKeyword(normalizedQuestionText, QuestionType.COLLABORATION)) {
                return QuestionType.COLLABORATION;
            }
            if (containsQuestionTypeKeyword(normalizedQuestionText, QuestionType.TECH)) {
                return QuestionType.TECH;
            }
            if (containsQuestionTypeKeyword(normalizedQuestionText, QuestionType.PROJECT)) {
                return QuestionType.PROJECT;
            }
        }

        return null;
    }

    private boolean containsQuestionTypeKeyword(String normalizedQuestionText, QuestionType questionType) {
        return followupRulesProperties.getQuestionTypeFallback()
                .getOrDefault(questionType, List.of())
                .stream()
                .map(keyword -> keyword == null ? "" : keyword.trim().toLowerCase())
                .filter(keyword -> !keyword.isBlank())
                .anyMatch(normalizedQuestionText::contains);
    }

    private String normalizeQuestionText(String questionText) {
        return questionText == null ? "" : questionText.trim().toLowerCase();
    }

    private boolean hasRuntimeDynamicBudgetBeenUsed(long sessionId) {
        List<InterviewSessionQuestion> runtimeFollowupQuestions = interviewSessionQuestionRepository
                .findAllBySessionIdOrderByQuestionOrderAsc(sessionId)
                .stream()
                .filter(sessionQuestion -> sessionQuestion.getQuestionType() == InterviewQuestionType.FOLLOW_UP)
                .filter(sessionQuestion -> sessionQuestion.getSourceQuestion() == null)
                .filter(sessionQuestion -> sessionQuestion.getParentSessionQuestion() != null)
                .toList();
        if (runtimeFollowupQuestions.isEmpty()) {
            return false;
        }

        Map<Long, InterviewAnswer> answersBySessionQuestionId = new LinkedHashMap<>();
        for (InterviewAnswer answer : interviewAnswerRepository.findAllWithSessionQuestionBySessionIdOrderByAnswerOrderAsc(sessionId)) {
            answersBySessionQuestionId.put(answer.getSessionQuestion().getId(), answer);
        }

        for (InterviewSessionQuestion runtimeFollowupQuestion : runtimeFollowupQuestions) {
            InterviewSessionQuestion parentQuestion = runtimeFollowupQuestion.getParentSessionQuestion();
            if (parentQuestion == null) {
                continue;
            }

            InterviewAnswer parentAnswer = answersBySessionQuestionId.get(parentQuestion.getId());
            FollowupAnalyzeResponse analysis = parentAnswer == null ? null : analyzeFollowup(parentQuestion, parentAnswer);
            if (analysis != null && analysis.finalAction() == FinalAction.USE_DYNAMIC) {
                return true;
            }
        }

        return false;
    }

    private InterviewFollowupGenerationService.FollowupGenerationRequest buildFollowupGenerationRequest(
            InterviewSession session,
            InterviewSessionQuestion parentQuestion,
            InterviewAnswer answer,
            int followUpDepth
    ) {
        return new InterviewFollowupGenerationService.FollowupGenerationRequest(
                session.getQuestionSet().getApplication().getJobRole(),
                session.getQuestionSet().getApplication().getCompanyName(),
                new com.back.backend.domain.ai.pipeline.payload.InterviewFollowupPayloadBuilder.CurrentQuestion(
                        parentQuestion.getQuestionOrder(),
                        parentQuestion.getQuestionType().getValue(),
                        parentQuestion.getQuestionText(),
                        parentQuestion.getDifficultyLevel().getValue()
                ),
                new com.back.backend.domain.ai.pipeline.payload.InterviewFollowupPayloadBuilder.CurrentAnswer(
                        answer.getAnswerText(),
                        answer.isSkipped()
                ),
                followUpDepth,
                MAX_FOLLOWUP_DEPTH
        );
    }

    private ServiceException remainingQuestionsIncomplete() {
        return new ServiceException(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                HttpStatus.BAD_REQUEST,
                "모든 질문에 답변한 뒤 세션을 종료해주세요.",
                false,
                List.of(new FieldErrorDetail("remainingQuestionCount", "incomplete"))
        );
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
        long totalQuestionCount = interviewSessionQuestionRepository.countBySessionId(session.getId());
        long answeredQuestionCount = interviewAnswerRepository.countBySessionId(session.getId());
        long remainingQuestionCount = Math.max(totalQuestionCount - answeredQuestionCount, 0);

        if (remainingQuestionCount > 0) {
            throw remainingQuestionsIncomplete();
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

    private void createSessionQuestionSnapshot(InterviewSession session) {
        List<InterviewQuestion> sourceQuestions = interviewQuestionRepository
                .findAllByQuestionSetIdOrderByQuestionOrderAsc(session.getQuestionSet().getId());
        if (sourceQuestions.isEmpty()) {
            return;
        }

        Map<Long, InterviewSessionQuestion> sessionQuestionsBySourceId = new LinkedHashMap<>();
        List<InterviewSessionQuestion> snapshotQuestions = sourceQuestions.stream()
                .map(sourceQuestion -> {
                    InterviewSessionQuestion sessionQuestion = InterviewSessionQuestion.builder()
                            .session(session)
                            .sourceQuestion(sourceQuestion)
                            .questionOrder(sourceQuestion.getQuestionOrder())
                            .questionType(sourceQuestion.getQuestionType())
                            .difficultyLevel(sourceQuestion.getDifficultyLevel())
                            .questionText(sourceQuestion.getQuestionText())
                            .build();
                    sessionQuestionsBySourceId.put(sourceQuestion.getId(), sessionQuestion);
                    return sessionQuestion;
                })
                .toList();

        interviewSessionQuestionRepository.saveAll(snapshotQuestions);

        for (InterviewQuestion sourceQuestion : sourceQuestions) {
            if (sourceQuestion.getParentQuestion() == null) {
                continue;
            }

            InterviewSessionQuestion childQuestion = sessionQuestionsBySourceId.get(sourceQuestion.getId());
            InterviewSessionQuestion parentQuestion = sessionQuestionsBySourceId.get(sourceQuestion.getParentQuestion().getId());
            childQuestion.changeParentSessionQuestion(parentQuestion);
        }
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
    }

    private <T> T executeNullableInTransaction(Supplier<T> action) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }

    private void executeWithoutResultInTransaction(Runnable action) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private <T> T executeReadOnlyInTransaction(Supplier<T> action) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
    }

    private record SessionCompletionPreparation(
            long sessionId,
            long questionSetId,
            Instant endedAt,
            List<InterviewAnswer> answers,
            String jobRole
    ) {
    }

    private record CompletionFollowupReviewPreparation(
            long sessionId,
            boolean reviewNeeded,
            InterviewCompletionFollowupGenerationService.CompletionFollowupGenerationRequest request
    ) {

        private static CompletionFollowupReviewPreparation reviewSkipped(long sessionId) {
            return new CompletionFollowupReviewPreparation(sessionId, false, null);
        }

        private static CompletionFollowupReviewPreparation reviewPending(
                long sessionId,
                InterviewCompletionFollowupGenerationService.CompletionFollowupGenerationRequest request
        ) {
            return new CompletionFollowupReviewPreparation(sessionId, true, request);
        }
    }

    private record CompletionFollowupReviewOutcome(boolean followupInserted) {

        private static CompletionFollowupReviewOutcome inserted() {
            return new CompletionFollowupReviewOutcome(true);
        }

        private static CompletionFollowupReviewOutcome notInserted() {
            return new CompletionFollowupReviewOutcome(false);
        }
    }

    private record AnsweredQuestionThread(
            InterviewSessionQuestion rootQuestion,
            InterviewAnswer rootAnswer,
            FollowupAnalyzeResponse runtimeRuleSummary,
            InterviewSessionQuestion runtimeFollowupQuestion,
            InterviewAnswer runtimeFollowupAnswer
    ) {
        private int tailQuestionOrder() {
            return runtimeFollowupQuestion != null
                    ? runtimeFollowupQuestion.getQuestionOrder()
                    : rootQuestion.getQuestionOrder();
        }
    }

    private record PendingFollowupResolution(
            long answerId,
            long parentSessionQuestionId,
            FinalAction finalAction,
            FollowupQuestionDraft followupDraft,
            InterviewFollowupGenerationService.FollowupGenerationRequest aiRequest
    ) {

        private static PendingFollowupResolution resolveOnly(
                long answerId,
                long parentSessionQuestionId,
                FinalAction finalAction
        ) {
            return new PendingFollowupResolution(answerId, parentSessionQuestionId, finalAction, null, null);
        }

        private static PendingFollowupResolution withLocalDraft(
                long answerId,
                long parentSessionQuestionId,
                FinalAction finalAction,
                FollowupQuestionDraft followupDraft
        ) {
            return new PendingFollowupResolution(answerId, parentSessionQuestionId, finalAction, followupDraft, null);
        }

        private static PendingFollowupResolution withAiRequest(
                long answerId,
                long parentSessionQuestionId,
                FinalAction finalAction,
                InterviewFollowupGenerationService.FollowupGenerationRequest aiRequest
        ) {
            return new PendingFollowupResolution(answerId, parentSessionQuestionId, finalAction, null, aiRequest);
        }
    }
}
