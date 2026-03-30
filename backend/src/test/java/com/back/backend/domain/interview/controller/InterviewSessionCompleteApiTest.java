package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
    }

    @Test
    void completeSession_returns200AndStoresFeedbackCompletedResult() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-success");
        InterviewSession session = fixture.session();

        given(interviewResultGenerationService.generate(eq(session.getId()), eq(fixture.questionSet().getId()), anyList()))
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

        given(interviewResultGenerationService.generate(eq(session.getId()), eq(fixture.questionSet().getId()), anyList()))
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

        given(interviewResultGenerationService.generate(eq(session.getId()), eq(fixture.questionSet().getId()), anyList()))
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

        given(interviewResultGenerationService.generate(eq(session.getId()), eq(fixture.questionSet().getId()), anyList()))
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
                .generate(eq(session.getId()), eq(fixture.questionSet().getId()), anyList());
    }

    @Test
    void getSessionResult_retriesGenerationAfterCooldownExpires() throws Exception {
        UserFixture fixture = persistAnsweredSession("complete-recheck-after-cooldown");
        InterviewSession session = fixture.session();

        given(clock.instant()).willReturn(
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW.plusSeconds(31)
        );
        given(interviewResultGenerationService.generate(eq(session.getId()), eq(fixture.questionSet().getId()), anyList()))
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
                .generate(eq(session.getId()), eq(fixture.questionSet().getId()), anyList());
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
        List<InterviewAnswer> answers = List.of(
                persistAnswer(session, firstQuestion, 1, VALID_ANSWER + " 첫 번째"),
                persistAnswer(session, secondQuestion, 2, VALID_ANSWER + " 두 번째"),
                persistAnswer(session, thirdQuestion, 3, VALID_ANSWER + " 세 번째")
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
        List<InterviewAnswer> answers = List.of(
                persistAnswer(session, firstQuestion, 1, VALID_ANSWER + " 첫 번째"),
                persistAnswer(session, secondQuestion, 2, VALID_ANSWER + " 두 번째")
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
        return session;
    }

    private InterviewAnswer persistAnswer(
            InterviewSession session,
            InterviewQuestion question,
            int answerOrder,
            String answerText
    ) {
        InterviewAnswer answer = InterviewAnswer.builder()
                .session(session)
                .question(question)
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
