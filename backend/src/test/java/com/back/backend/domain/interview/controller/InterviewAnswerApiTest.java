package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.interview.dto.request.SubmitInterviewAnswerRequest;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewAnswer;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewAnswerRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewAnswerApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final String VALID_ANSWER = "서비스 장애 원인을 추적할 때 로그와 메트릭을 함께 비교하며 원인 후보를 단계적으로 줄였습니다.";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InterviewAnswerRepository interviewAnswerRepository;

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
    }

    @Test
    void submitAnswer_returns201AndSavesTrimmedAnswer() throws Exception {
        User user = persistUser("answer-submit@example.com", "answer-submit");
        Instant previousActivityAt = FIXED_NOW.minus(Duration.ofMinutes(5));
        InterviewSession session = persistSession(user, InterviewSessionStatus.IN_PROGRESS, previousActivityAt);
        InterviewQuestion currentQuestion = persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                currentQuestion.getId(),
                                1,
                                "  " + VALID_ANSWER + "  ",
                                false
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.data.questionId").value(currentQuestion.getId()))
                .andExpect(jsonPath("$.data.answerOrder").value(1))
                .andExpect(jsonPath("$.data.isSkipped").value(false))
                .andExpect(jsonPath("$.data.submittedAt").isNotEmpty());

        entityManager.flush();
        entityManager.clear();

        InterviewAnswer savedAnswer = interviewAnswerRepository.findAll().get(0);
        InterviewSession refreshedSession = entityManager.find(InterviewSession.class, session.getId());
        assertThat(savedAnswer.getAnswerOrder()).isEqualTo(1);
        assertThat(savedAnswer.getAnswerText()).isEqualTo(VALID_ANSWER);
        assertThat(savedAnswer.isSkipped()).isFalse();
        assertThat(refreshedSession.getLastActivityAt()).isAfter(previousActivityAt);
    }

    @Test
    void submitAnswer_returns201WhenSkippedAndStoresNullText() throws Exception {
        User user = persistUser("answer-skip@example.com", "answer-skip");
        InterviewSession session = persistInProgressSession(user);
        InterviewQuestion currentQuestion = persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                currentQuestion.getId(),
                                1,
                                null,
                                true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isSkipped").value(true));

        entityManager.flush();
        entityManager.clear();

        InterviewAnswer savedAnswer = interviewAnswerRepository.findAll().get(0);
        assertThat(savedAnswer.isSkipped()).isTrue();
        assertThat(savedAnswer.getAnswerText()).isNull();
    }

    @Test
    void submitAnswer_returns404WhenSessionIsNotOwned() throws Exception {
        User owner = persistUser("answer-owner@example.com", "answer-owner");
        User otherUser = persistUser("answer-other@example.com", "answer-other");
        InterviewSession session = persistInProgressSession(owner);
        InterviewQuestion currentQuestion = persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(otherUser.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                currentQuestion.getId(),
                                1,
                                VALID_ANSWER,
                                false
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void submitAnswer_returns404WhenQuestionDoesNotBelongToSession() throws Exception {
        User user = persistUser("answer-question-missing@example.com", "answer-question-missing");
        InterviewSession session = persistInProgressSession(user);
        persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        Application otherApplication = persistApplication(user, "other-application");
        InterviewQuestionSet otherQuestionSet = persistQuestionSet(user, otherApplication, 1);
        InterviewQuestion foreignQuestion = persistQuestion(otherQuestionSet, 1, "다른 질문 세트 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                foreignQuestion.getId(),
                                1,
                                VALID_ANSWER,
                                false
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void submitAnswer_returns400WhenAnswerIsRequired() throws Exception {
        User user = persistUser("answer-required@example.com", "answer-required");
        InterviewSession session = persistInProgressSession(user);
        InterviewQuestion currentQuestion = persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                currentQuestion.getId(),
                                1,
                                "   ",
                                false
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_ANSWER_REQUIRED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("answerText"));
    }

    @Test
    void submitAnswer_returns400WhenAnswerIsTooShort() throws Exception {
        User user = persistUser("answer-short@example.com", "answer-short");
        InterviewSession session = persistInProgressSession(user);
        InterviewQuestion currentQuestion = persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                currentQuestion.getId(),
                                1,
                                "짧은 답변입니다.",
                                false
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_ANSWER_TOO_SHORT.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("answerText"));
    }

    @Test
    void submitAnswer_returns400WhenQuestionOrderDoesNotMatchCurrentTurn() throws Exception {
        User user = persistUser("answer-sequence@example.com", "answer-sequence");
        InterviewSession session = persistInProgressSession(user);
        persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        InterviewQuestion secondQuestion = persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                secondQuestion.getId(),
                                2,
                                VALID_ANSWER,
                                false
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()));
    }

    @Test
    void submitAnswer_returns409WhenSessionIsPaused() throws Exception {
        User user = persistUser("answer-paused@example.com", "answer-paused");
        InterviewSession session = persistSession(user, InterviewSessionStatus.PAUSED, FIXED_NOW);
        InterviewQuestion currentQuestion = persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                currentQuestion.getId(),
                                1,
                                VALID_ANSWER,
                                false
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_NOT_ACTIVE.name()));
    }

    @Test
    void submitAnswer_returns409WhenSessionAlreadyCompleted() throws Exception {
        User user = persistUser("answer-completed@example.com", "answer-completed");
        InterviewSession session = persistSession(user, InterviewSessionStatus.COMPLETED, FIXED_NOW);
        InterviewQuestion currentQuestion = persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                currentQuestion.getId(),
                                1,
                                VALID_ANSWER,
                                false
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED.name()));
    }

    @Test
    void submitAnswer_autoPausesExpiredSessionAndReturns409() throws Exception {
        User user = persistUser("answer-autopause@example.com", "answer-autopause");
        // 31분 전 활동 시각으로 auto-pause 경계를 넘긴 세션을 만든다.
        // 답변 제출 시 충돌 오류를 내면서도 paused 상태 보정은 저장되는지 본다.
        InterviewSession session = persistSession(user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW.minus(Duration.ofMinutes(31)));
        InterviewQuestion currentQuestion = persistQuestion(session.getQuestionSet(), 1, "첫 번째 질문");
        persistQuestion(session.getQuestionSet(), 2, "두 번째 질문");
        persistQuestion(session.getQuestionSet(), 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/answers", session.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SubmitInterviewAnswerRequest(
                                currentQuestion.getId(),
                                1,
                                VALID_ANSWER,
                                false
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_NOT_ACTIVE.name()));

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = entityManager.find(InterviewSession.class, session.getId());
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.PAUSED);
        assertThat(interviewAnswerRepository.findAll()).isEmpty();
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }

    private User persistUser(String email, String displayName) {
        User user = User.builder()
                .email(email)
                .displayName(displayName)
                .profileImageUrl("https://example.com/profile.png")
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Application persistApplication(User user, String title) {
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

    private InterviewQuestionSet persistQuestionSet(User user, Application application, int questionCount) {
        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title("질문 세트")
                .questionCount(questionCount)
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

    private InterviewSession persistInProgressSession(User user) {
        return persistSession(user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);
    }

    private InterviewSession persistSession(User user, InterviewSessionStatus status, Instant startedAt) {
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 3);
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(startedAt)
                .lastActivityAt(startedAt)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }
}
