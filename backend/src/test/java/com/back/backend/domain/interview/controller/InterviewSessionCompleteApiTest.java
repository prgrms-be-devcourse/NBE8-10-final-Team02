package com.back.backend.domain.interview.controller;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSessionQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewAnswerRepository;
import com.back.backend.domain.interview.repository.InterviewAnswerTagRepository;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.domain.interview.service.InterviewResultGenerationService;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.back.backend.domain.interview.support.InterviewSessionQuestionTestHelper.findSessionQuestion;
import static com.back.backend.domain.interview.support.InterviewSessionQuestionTestHelper.persistSessionQuestionSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewSessionCompleteApiTest extends ApiTestBase {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final String FOLLOWUP_TEMPLATE_ID = "ai.interview.followup.generate.v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String VALID_ANSWER =
            "장애 원인을 추적할 때 로그와 메트릭을 비교하고, 사용자 영향 범위를 수치로 확인한 뒤 복구 순서를 정했습니다.";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewAnswerRepository interviewAnswerRepository;

    @Autowired
    private InterviewAnswerTagRepository interviewAnswerTagRepository;

    @MockitoBean
    private InterviewResultGenerationService interviewResultGenerationService;

    @MockitoBean
    private AiPipeline aiPipeline;

    @BeforeEach
    void setUpClock() throws Exception {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(aiPipeline.execute(eq(FOLLOWUP_TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "followUpQuestion": null,
                          "qualityFlags": ["low_context"]
                        }
                        """));
    }

    @Test
    void completeSession_returns200AndStoresFeedbackCompletedResult() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-success");
        InterviewSession session = fixture.session();

        given(interviewResultGenerationService.generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString()))
                .willReturn(new InterviewResultGenerationService.GeneratedInterviewResult(
                        84,
                        "기술 선택 근거는 좋았지만 결과 지표를 더 명확히 제시하면 좋습니다.",
                        List.of(
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(
                                        1,
                                        80,
                                        "핵심 설명은 있었지만 수치 근거가 더 필요합니다.",
                                        List.of("근거 부족")
                                ),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(
                                        2,
                                        86,
                                        "문제 해결 흐름은 명확하지만 사례를 더 압축하면 좋습니다.",
                                        List.of("구체성 부족")
                                ),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(
                                        3,
                                        88,
                                        "선택 이유는 잘 설명했지만 trade-off를 더 드러낼 수 있습니다.",
                                        List.of("선택 근거 부족")
                                )
                        )
                ));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.data.status").value("feedback_completed"))
                .andExpect(jsonPath("$.data.totalScore").value(84))
                .andExpect(jsonPath("$.data.summaryFeedback")
                        .value("기술 선택 근거는 좋았지만 결과 지표를 더 명확히 제시하면 좋습니다."))
                .andExpect(jsonPath("$.data.endedAt").value(FIXED_NOW.toString()));

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = interviewSessionRepository.findById(session.getId()).orElseThrow();
        List<InterviewAnswer> refreshedAnswers = interviewAnswerRepository.findAllBySessionIdOrderByAnswerOrderAsc(session.getId());

        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.FEEDBACK_COMPLETED);
        assertThat(refreshedSession.getTotalScore()).isEqualTo(84);
        assertThat(refreshedSession.getSummaryFeedback()).isEqualTo("기술 선택 근거는 좋았지만 결과 지표를 더 명확히 제시하면 좋습니다.");
        assertThat(refreshedSession.getEndedAt()).isEqualTo(FIXED_NOW);
        assertThat(refreshedAnswers)
                .extracting(InterviewAnswer::getScore)
                .containsExactly(80, 86, 88);
        assertThat(interviewAnswerTagRepository.findAll()).hasSize(3);
    }

    @Test
    void completeSession_returns200AndStoresFeedbackCompletedResultWithDynamicFollowupAnswer() throws Exception {
        UserFixture fixture = persistAnsweredSessionWithDynamicFollowup("complete-followup-success", true);
        InterviewSession session = fixture.session();

        given(interviewResultGenerationService.generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString()))
                .willReturn(new InterviewResultGenerationService.GeneratedInterviewResult(
                        86,
                        "동적 꼬리질문 답변까지 포함해 평가했습니다.",
                        List.of(
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(1, 80, "첫 번째 평가", List.of("근거 부족")),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(2, 84, "follow-up 평가", List.of("구체성 부족")),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(3, 86, "세 번째 평가", List.of()),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(4, 88, "네 번째 평가", List.of("선택 근거 부족"))
                        )
                ));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("feedback_completed"))
                .andExpect(jsonPath("$.data.totalScore").value(86))
                .andExpect(jsonPath("$.data.summaryFeedback").value("동적 꼬리질문 답변까지 포함해 평가했습니다."));

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = interviewSessionRepository.findById(session.getId()).orElseThrow();
        List<InterviewAnswer> refreshedAnswers = interviewAnswerRepository.findAllBySessionIdOrderByAnswerOrderAsc(session.getId());

        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.FEEDBACK_COMPLETED);
        assertThat(refreshedAnswers).hasSize(4);
        assertThat(refreshedAnswers)
                .extracting(InterviewAnswer::getScore)
                .containsExactly(80, 84, 86, 88);
    }

    @Test
    void completeSession_returns400WhenRemainingQuestionsExist() throws Exception {
        UserFixture fixture = persistPartiallyAnsweredSession("complete-incomplete");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", fixture.session().getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("remainingQuestionCount"));

        then(interviewResultGenerationService).shouldHaveNoInteractions();
    }

    @Test
    void completeSession_returns400WhenDynamicFollowupQuestionRemainsUnanswered() throws Exception {
        UserFixture fixture = persistAnsweredSessionWithDynamicFollowup("complete-followup-incomplete", false);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", fixture.session().getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("remainingQuestionCount"));

        then(interviewResultGenerationService).shouldHaveNoInteractions();
    }

    @Test
    void completeSession_returns400WhenCompletionReviewAddsLastFollowup() throws Exception {
        UserFixture fixture = persistAnsweredSessionForCompletionReview("complete-last-followup");
        InterviewSession session = fixture.session();

        given(aiPipeline.execute(eq(FOLLOWUP_TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "followUpQuestion": {
                            "questionType": "follow_up",
                            "difficultyLevel": "medium",
                            "questionText": "그때 어떤 기준으로 우선순위를 잡았는지 조금 더 구체적으로 설명해주실 수 있나요?",
                            "parentQuestionOrder": 3
                          },
                          "qualityFlags": []
                        }
                        """));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("remainingQuestionCount"));

        then(interviewResultGenerationService).shouldHaveNoInteractions();

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = interviewSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(refreshedSession.getEndedAt()).isNull();
        assertThat(refreshedSession.getCompletionFollowupReviewedAt()).isNotNull();
        assertThat(entityManager.createQuery(
                        "select count(q) from InterviewSessionQuestion q where q.session.id = :sessionId",
                        Long.class
                )
                .setParameter("sessionId", session.getId())
                .getSingleResult()).isEqualTo(4L);

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentQuestion.questionType").value("follow_up"));
    }

    @Test
    void completeSession_continuesWhenCompletionReviewReturnsNull() throws Exception {
        UserFixture fixture = persistAnsweredSessionForCompletionReview("complete-last-followup-null");
        InterviewSession session = fixture.session();

        given(interviewResultGenerationService.generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString()))
                .willReturn(new InterviewResultGenerationService.GeneratedInterviewResult(
                        83,
                        "마지막 보완 검토 후 바로 결과를 생성했습니다.",
                        List.of(
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(1, 80, "첫 번째 평가", List.of("근거 부족")),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(2, 82, "두 번째 평가", List.of()),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(3, 87, "세 번째 평가", List.of("선택 근거 부족"))
                        )
                ));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("feedback_completed"))
                .andExpect(jsonPath("$.data.totalScore").value(83));

        then(aiPipeline).should(times(1)).execute(eq(FOLLOWUP_TEMPLATE_ID), anyString());

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = interviewSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(refreshedSession.getCompletionFollowupReviewedAt()).isNotNull();
        assertThat(entityManager.createQuery(
                        "select count(q) from InterviewSessionQuestion q where q.session.id = :sessionId",
                        Long.class
                )
                .setParameter("sessionId", session.getId())
                .getSingleResult()).isEqualTo(3L);
    }

    @Test
    void completeSession_reviewsOnlyOnceAfterLastFollowupIsAnswered() throws Exception {
        UserFixture fixture = persistAnsweredSessionForCompletionReview("complete-last-followup-once");
        InterviewSession session = fixture.session();

        given(aiPipeline.execute(eq(FOLLOWUP_TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "followUpQuestion": {
                            "questionType": "follow_up",
                            "difficultyLevel": "medium",
                            "questionText": "그때 어떤 기준으로 우선순위를 잡았는지 조금 더 구체적으로 설명해주실 수 있나요?",
                            "parentQuestionOrder": 3
                          },
                          "qualityFlags": []
                        }
                        """));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isBadRequest());

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = interviewSessionRepository.findById(session.getId()).orElseThrow();
        InterviewSessionQuestion generatedFollowupQuestion = entityManager.createQuery(
                        """
                                select q
                                from InterviewSessionQuestion q
                                where q.session.id = :sessionId
                                  and q.questionType = :questionType
                                order by q.questionOrder desc
                                """,
                        InterviewSessionQuestion.class
                )
                .setParameter("sessionId", refreshedSession.getId())
                .setParameter("questionType", InterviewQuestionType.FOLLOW_UP)
                .setMaxResults(1)
                .getSingleResult();
        persistAnswer(
                refreshedSession,
                generatedFollowupQuestion,
                4,
                VALID_ANSWER + " 마지막 보완 답변"
        );

        given(interviewResultGenerationService.generate(anyLong(),eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString()))
                .willReturn(new InterviewResultGenerationService.GeneratedInterviewResult(
                        85,
                        "마지막 보완 질문까지 반영해 결과를 생성했습니다.",
                        List.of(
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(1, 80, "첫 번째 평가", List.of("근거 부족")),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(2, 82, "두 번째 평가", List.of()),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(3, 84, "세 번째 평가", List.of("선택 근거 부족")),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(4, 88, "보완 질문 평가", List.of())
                        )
                ));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("feedback_completed"))
                .andExpect(jsonPath("$.data.totalScore").value(85));

        then(aiPipeline).should(times(1)).execute(eq(FOLLOWUP_TEMPLATE_ID), anyString());
        then(interviewResultGenerationService).should(times(1))
                .generate(anyLong(),eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString());
    }

    @Test
    void completeSession_returns404WhenSessionIsNotOwned() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-not-owned");
        UserFixture otherUser = persistUserFixture("complete-other");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", fixture.session().getId())
                        .with(authenticated(otherUser.user().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void completeSession_returns409WhenSessionAlreadyCompleted() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-already");
        fixture.session().changeStatus(InterviewSessionStatus.COMPLETED);
        fixture.session().changeEndedAt(FIXED_NOW);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", fixture.session().getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED.name()));
    }

    @Test
    void completeSession_returns502AndKeepsCompletedSessionWhenResultIsIncomplete() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-incomplete-result");
        InterviewSession session = fixture.session();

        given(interviewResultGenerationService.generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString()))
                .willThrow(new ServiceException(
                        ErrorCode.INTERVIEW_RESULT_INCOMPLETE,
                        HttpStatus.BAD_GATEWAY,
                        "면접 결과 생성 결과가 완전하지 않습니다.",
                        true
                ));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_RESULT_INCOMPLETE.name()))
                .andExpect(jsonPath("$.error.retryable").value(true));

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = interviewSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(refreshedSession.getEndedAt()).isEqualTo(FIXED_NOW);
        assertThat(refreshedSession.getTotalScore()).isNull();
        assertThat(refreshedSession.getSummaryFeedback()).isNull();
    }

    @Test
    void completeSession_returns503AndKeepsCompletedSessionWhenAiIsUnavailable() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-service-unavailable");
        InterviewSession session = fixture.session();

        given(interviewResultGenerationService.generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString()))
                .willThrow(new ServiceException(
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "외부 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.",
                        true
                ));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE.name()))
                .andExpect(jsonPath("$.error.retryable").value(true));

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = interviewSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(refreshedSession.getEndedAt()).isEqualTo(FIXED_NOW);
        assertThat(refreshedSession.getTotalScore()).isNull();
        assertThat(refreshedSession.getSummaryFeedback()).isNull();
    }

    @Test
    void completeSession_blocksPostRetryAndKeepsResultRecheckPathWhenGenerationFails() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-recheck-flow");
        InterviewSession session = fixture.session();

        given(interviewResultGenerationService.generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString()))
                .willThrow(new ServiceException(
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "외부 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.",
                        true
                ));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE.name()))
                .andExpect(jsonPath("$.error.retryable").value(true));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED.name()))
                .andExpect(jsonPath("$.error.retryable").value(false));

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}/result", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_RESULT_INCOMPLETE.name()))
                .andExpect(jsonPath("$.error.retryable").value(true));

        then(interviewResultGenerationService).should(times(1))
                .generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString());
    }

    @Test
    void getSessionResult_retriesGenerationAfterCooldownExpires() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-recheck-after-cooldown");
        InterviewSession session = fixture.session();

        given(clock.instant()).willReturn(
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW.plusSeconds(31)
        );
        given(interviewResultGenerationService.generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString()))
                .willThrow(new ServiceException(
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "외부 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.",
                        true
                ))
                .willReturn(new InterviewResultGenerationService.GeneratedInterviewResult(
                        84,
                        "기술 선택 근거는 좋았지만 결과 지표를 더 명확히 제시하면 좋습니다.",
                        List.of(
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(
                                        1,
                                        80,
                                        "핵심 설명은 있었지만 수치 근거가 더 필요합니다.",
                                        List.of("근거 부족")
                                ),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(
                                        2,
                                        86,
                                        "문제 해결 흐름은 명확하지만 사례를 더 압축하면 좋습니다.",
                                        List.of("구체성 부족")
                                ),
                                new InterviewResultGenerationService.GeneratedInterviewAnswerResult(
                                        3,
                                        88,
                                        "선택 이유는 잘 설명했지만 trade-off를 더 드러낼 수 있습니다.",
                                        List.of("선택 근거 부족")
                                )
                        )
                ));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/complete", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}/result", session.getId())
                        .with(authenticated(fixture.user().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("feedback_completed"))
                .andExpect(jsonPath("$.data.totalScore").value(84));

        then(interviewResultGenerationService).should(times(2))
                .generate(anyLong(), eq(session.getId()), eq(fixture.questionSet().getId()), anyList(), anyString());
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }

    private UserFixture persistUserFixture(String prefix) {
        com.back.backend.domain.user.entity.User user = com.back.backend.domain.user.entity.User.builder()
                .email(prefix + "@example.com")
                .displayName(prefix)
                .profileImageUrl("https://example.com/profile.png")
                .status(com.back.backend.domain.user.entity.UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return new UserFixture(user, null, null, List.of());
    }

    private UserFixture persistAnsweredSession(String prefix) {
        com.back.backend.domain.user.entity.User user = persistUserFixture(prefix).user();
        Application application = persistApplication(user, prefix + "-application");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application);
        InterviewQuestion firstQuestion = persistQuestion(questionSet, 1, "첫 번째 질문");
        InterviewQuestion secondQuestion = persistQuestion(questionSet, 2, "두 번째 질문");
        InterviewQuestion thirdQuestion = persistQuestion(questionSet, 3, "세 번째 질문");
        InterviewSession session = persistSession(user, questionSet, InterviewSessionStatus.IN_PROGRESS);
        InterviewSessionQuestion firstSessionQuestion = findSessionQuestion(entityManager, session, 1);
        InterviewSessionQuestion secondSessionQuestion = findSessionQuestion(entityManager, session, 2);
        InterviewSessionQuestion thirdSessionQuestion = findSessionQuestion(entityManager, session, 3);
        List<InterviewAnswer> answers = List.of(
                persistAnswer(session, firstSessionQuestion, 1, VALID_ANSWER + " 첫 번째"),
                persistAnswer(session, secondSessionQuestion, 2, VALID_ANSWER + " 두 번째"),
                persistAnswer(session, thirdSessionQuestion, 3, VALID_ANSWER + " 세 번째")
        );
        return new UserFixture(user, questionSet, session, answers);
    }

    private UserFixture persistAnsweredSessionWithDynamicFollowup(String prefix, boolean includeFollowupAnswer) {
        com.back.backend.domain.user.entity.User user = persistUserFixture(prefix).user();
        Application application = persistApplication(user, prefix + "-application");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application);
        InterviewQuestion firstQuestion = persistQuestion(questionSet, 1, "첫 번째 질문");
        InterviewQuestion secondQuestion = persistQuestion(questionSet, 2, "두 번째 질문");
        InterviewQuestion thirdQuestion = persistQuestion(questionSet, 3, "세 번째 질문");
        InterviewSession session = persistSessionWithoutSnapshot(user, questionSet, InterviewSessionStatus.IN_PROGRESS);
        InterviewSessionQuestion firstSessionQuestion = persistSessionQuestion(
                session,
                firstQuestion,
                null,
                1,
                InterviewQuestionType.PROJECT,
                "첫 번째 질문"
        );
        InterviewSessionQuestion followupSessionQuestion = persistSessionQuestion(
                session,
                null,
                firstSessionQuestion,
                2,
                InterviewQuestionType.FOLLOW_UP,
                "방금 답변한 선택 기준을 조금 더 구체적으로 설명해주실 수 있나요?"
        );
        InterviewSessionQuestion secondSessionQuestion = persistSessionQuestion(
                session,
                secondQuestion,
                null,
                3,
                InterviewQuestionType.PROJECT,
                "두 번째 질문"
        );
        InterviewSessionQuestion thirdSessionQuestion = persistSessionQuestion(
                session,
                thirdQuestion,
                null,
                4,
                InterviewQuestionType.PROJECT,
                "세 번째 질문"
        );

        List<InterviewAnswer> answers = includeFollowupAnswer
                ? List.of(
                        persistAnswer(session, firstSessionQuestion, 1, VALID_ANSWER + " 첫 번째"),
                        persistAnswer(session, followupSessionQuestion, 2, VALID_ANSWER + " follow-up"),
                        persistAnswer(session, secondSessionQuestion, 3, VALID_ANSWER + " 두 번째"),
                        persistAnswer(session, thirdSessionQuestion, 4, VALID_ANSWER + " 세 번째")
                )
                : List.of(
                        persistAnswer(session, firstSessionQuestion, 1, VALID_ANSWER + " 첫 번째"),
                        persistAnswer(session, secondSessionQuestion, 2, VALID_ANSWER + " 두 번째"),
                        persistAnswer(session, thirdSessionQuestion, 3, VALID_ANSWER + " 세 번째")
                );
        return new UserFixture(user, questionSet, session, answers);
    }

    private UserFixture persistPartiallyAnsweredSession(String prefix) {
        com.back.backend.domain.user.entity.User user = persistUserFixture(prefix).user();
        Application application = persistApplication(user, prefix + "-application");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application);
        InterviewQuestion firstQuestion = persistQuestion(questionSet, 1, "첫 번째 질문");
        InterviewQuestion secondQuestion = persistQuestion(questionSet, 2, "두 번째 질문");
        persistQuestion(questionSet, 3, "세 번째 질문");
        InterviewSession session = persistSession(user, questionSet, InterviewSessionStatus.IN_PROGRESS);
        InterviewSessionQuestion firstSessionQuestion = findSessionQuestion(entityManager, session, 1);
        InterviewSessionQuestion secondSessionQuestion = findSessionQuestion(entityManager, session, 2);
        List<InterviewAnswer> answers = List.of(
                persistAnswer(session, firstSessionQuestion, 1, VALID_ANSWER + " 첫 번째"),
                persistAnswer(session, secondSessionQuestion, 2, VALID_ANSWER + " 두 번째")
        );
        return new UserFixture(user, questionSet, session, answers);
    }

    private UserFixture persistAnsweredSessionForCompletionReview(String prefix) {
        com.back.backend.domain.user.entity.User user = persistUserFixture(prefix).user();
        Application application = persistApplication(user, prefix + "-application");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application);
        InterviewQuestion firstQuestion = persistQuestion(questionSet, 1, "첫 번째 질문");
        InterviewQuestion secondQuestion = persistQuestion(questionSet, 2, "두 번째 질문");
        InterviewQuestion thirdQuestion = persistQuestion(questionSet, 3, "세 번째 질문");
        InterviewSession session = persistSession(user, questionSet, InterviewSessionStatus.IN_PROGRESS);
        InterviewSessionQuestion firstSessionQuestion = findSessionQuestion(entityManager, session, 1);
        InterviewSessionQuestion secondSessionQuestion = findSessionQuestion(entityManager, session, 2);
        InterviewSessionQuestion thirdSessionQuestion = findSessionQuestion(entityManager, session, 3);
        List<InterviewAnswer> answers = List.of(
                persistAnswer(session, firstSessionQuestion, 1, VALID_ANSWER + " 첫 번째"),
                persistAnswer(session, secondSessionQuestion, 2, VALID_ANSWER + " 두 번째"),
                persistAnswer(
                        session,
                        thirdSessionQuestion,
                        3,
                        "사내 재고 관리 시스템을 만드는 프로젝트가 있었는데, 기존 엑셀 작업을 옮겨오는 성격이라 요구사항이 자주 바뀌었습니다. "
                                + "저는 백엔드 쪽 기본 CRUD와 배치 작업을 맡아 필요한 기능을 우선 붙였습니다. "
                                + "일정은 맞췄지만 어떤 기준으로 우선순위를 잡았는지나 결과가 얼마나 안정화됐는지는 "
                                + "지금 설명하면 조금 일반론적으로 들릴 수 있습니다."
                )
        );
        return new UserFixture(user, questionSet, session, answers);
    }

    private Application persistApplication(com.back.backend.domain.user.entity.User user, String title) {
        Application application = Application.builder()
                .user(user)
                .applicationTitle(title)
                .companyName(title + "-company")
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(ApplicationStatus.READY)
                .build();
        entityManager.persist(application);
        entityManager.flush();
        return application;
    }

    private InterviewQuestionSet persistQuestionSet(
            com.back.backend.domain.user.entity.User user,
            Application application
    ) {
        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title("질문 세트")
                .questionCount(3)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"behavioral"})
                .build();
        entityManager.persist(questionSet);
        entityManager.flush();
        return questionSet;
    }

    private InterviewQuestion persistQuestion(InterviewQuestionSet questionSet, int order, String questionText) {
        InterviewQuestion question = InterviewQuestion.builder()
                .questionSet(questionSet)
                .questionOrder(order)
                .questionType(InterviewQuestionType.PROJECT)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionText(questionText)
                .build();
        entityManager.persist(question);
        entityManager.flush();
        return question;
    }

    private InterviewSession persistSession(
            com.back.backend.domain.user.entity.User user,
            InterviewQuestionSet questionSet,
            InterviewSessionStatus status
    ) {
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(FIXED_NOW)
                .lastActivityAt(FIXED_NOW)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        persistSessionQuestionSnapshot(entityManager, session);
        return session;
    }

    private InterviewSession persistSessionWithoutSnapshot(
            com.back.backend.domain.user.entity.User user,
            InterviewQuestionSet questionSet,
            InterviewSessionStatus status
    ) {
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(FIXED_NOW)
                .lastActivityAt(FIXED_NOW)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private InterviewSessionQuestion persistSessionQuestion(
            InterviewSession session,
            InterviewQuestion sourceQuestion,
            InterviewSessionQuestion parentSessionQuestion,
            int questionOrder,
            InterviewQuestionType questionType,
            String questionText
    ) {
        InterviewSessionQuestion sessionQuestion = InterviewSessionQuestion.builder()
                .session(session)
                .sourceQuestion(sourceQuestion)
                .parentSessionQuestion(parentSessionQuestion)
                .questionOrder(questionOrder)
                .questionType(questionType)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionText(questionText)
                .build();
        entityManager.persist(sessionQuestion);
        entityManager.flush();
        return sessionQuestion;
    }

    private InterviewAnswer persistAnswer(
            InterviewSession session,
            InterviewSessionQuestion question,
            int answerOrder,
            String answerText
    ) {
        InterviewAnswer answer = InterviewAnswer.builder()
                .session(session)
                .sessionQuestion(question)
                .answerOrder(answerOrder)
                .answerText(answerText)
                .skipped(false)
                .build();
        entityManager.persist(answer);
        entityManager.flush();
        return answer;
    }

    private record UserFixture(
            com.back.backend.domain.user.entity.User user,
            InterviewQuestionSet questionSet,
            InterviewSession session,
            List<InterviewAnswer> answers
    ) {
    }
}
