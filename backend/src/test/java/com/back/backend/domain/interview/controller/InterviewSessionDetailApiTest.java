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
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
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
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewSessionDetailApiTest extends ApiTestBase {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
    }

    @Test
    void getSessionDetail_returns200ForPausedSession() throws Exception {
        User user = persistUser("detail-paused@example.com", "detail-paused");
        InterviewSession session = persistSession(user, InterviewSessionStatus.PAUSED, FIXED_NOW);
        InterviewQuestion firstQuestion = findQuestion(session, 1);
        InterviewQuestion secondQuestion = findQuestion(session, 2);
        persistAnswer(session, firstQuestion, 1, false, "첫 번째 답변입니다. 충분히 긴 답변으로 조건을 만족합니다.");

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(session.getId()))
                .andExpect(jsonPath("$.data.questionSetId").value(session.getQuestionSet().getId()))
                .andExpect(jsonPath("$.data.status").value("paused"))
                .andExpect(jsonPath("$.data.currentQuestion.id").value(secondQuestion.getId()))
                .andExpect(jsonPath("$.data.currentQuestion.questionOrder").value(2))
                .andExpect(jsonPath("$.data.totalQuestionCount").value(3))
                .andExpect(jsonPath("$.data.answeredQuestionCount").value(1))
                .andExpect(jsonPath("$.data.remainingQuestionCount").value(2))
                .andExpect(jsonPath("$.data.resumeAvailable").value(true))
                .andExpect(jsonPath("$.data.lastActivityAt").value(FIXED_NOW.toString()))
                .andExpect(jsonPath("$.data.startedAt").value(FIXED_NOW.toString()))
                .andExpect(jsonPath("$.data.endedAt").value(nullValue()));
    }

    @Test
    void getSessionDetail_returns200ForInProgressSession() throws Exception {
        User user = persistUser("detail-in-progress@example.com", "detail-in-progress");
        InterviewSession session = persistSession(user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);
        InterviewQuestion firstQuestion = findQuestion(session, 1);

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andExpect(jsonPath("$.data.currentQuestion.id").value(firstQuestion.getId()))
                .andExpect(jsonPath("$.data.currentQuestion.questionOrder").value(1))
                .andExpect(jsonPath("$.data.answeredQuestionCount").value(0))
                .andExpect(jsonPath("$.data.remainingQuestionCount").value(3))
                .andExpect(jsonPath("$.data.resumeAvailable").value(false));
    }

    @Test
    void getSessionDetail_returns404WhenSessionIsNotOwned() throws Exception {
        User owner = persistUser("detail-owner@example.com", "detail-owner");
        User otherUser = persistUser("detail-other@example.com", "detail-other");
        InterviewSession session = persistSession(owner, InterviewSessionStatus.PAUSED, FIXED_NOW);

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(otherUser.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void getSessionDetail_normalizesExpiredInProgressSessionToPaused() throws Exception {
        User user = persistUser("detail-expired@example.com", "detail-expired");
        // 30분 초과 무응답 조건을 확실히 넘겨 자동 일시정지 정규화가 발동하도록 잡는다.
        Instant expiredActivityAt = FIXED_NOW.minus(Duration.ofMinutes(31));
        InterviewSession session = persistSession(user, InterviewSessionStatus.IN_PROGRESS, expiredActivityAt);

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("paused"))
                .andExpect(jsonPath("$.data.resumeAvailable").value(true));

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = entityManager.find(InterviewSession.class, session.getId());
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.PAUSED);
        assertThat(refreshedSession.getLastActivityAt()).isEqualTo(refreshedSession.getStartedAt());
        assertThat(refreshedSession.getLastActivityAt()).isBefore(FIXED_NOW.minus(Duration.ofMinutes(30)));
    }

    @Test
    void getSessionDetail_returnsNullCurrentQuestionWhenAllQuestionsAnswered() throws Exception {
        User user = persistUser("detail-complete@example.com", "detail-complete");
        InterviewSession session = persistSession(user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);
        InterviewQuestion firstQuestion = findQuestion(session, 1);
        InterviewQuestion secondQuestion = findQuestion(session, 2);
        InterviewQuestion thirdQuestion = findQuestion(session, 3);
        // 마지막 질문까지 답변이 저장된 상태를 만들어 currentQuestion=null 계약을 확인한다.
        persistAnswer(session, firstQuestion, 1, false, "첫 번째 답변입니다. 충분히 긴 답변으로 조건을 만족합니다.");
        persistAnswer(session, secondQuestion, 2, false, "두 번째 답변입니다. 충분히 긴 답변으로 조건을 만족합니다.");
        persistAnswer(session, thirdQuestion, 3, true, null);

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentQuestion").value(nullValue()))
                .andExpect(jsonPath("$.data.answeredQuestionCount").value(3))
                .andExpect(jsonPath("$.data.remainingQuestionCount").value(0))
                .andExpect(jsonPath("$.data.resumeAvailable").value(false));
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

    private InterviewQuestionSet persistQuestionSet(User user, Application application) {
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
        persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");
        persistQuestion(questionSet, 3, "세 번째 질문");
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

    private InterviewQuestion findQuestion(InterviewSession session, int questionOrder) {
        return entityManager.createQuery(
                        "select q from InterviewQuestion q where q.questionSet.id = :questionSetId and q.questionOrder = :questionOrder",
                        InterviewQuestion.class
                )
                .setParameter("questionSetId", session.getQuestionSet().getId())
                .setParameter("questionOrder", questionOrder)
                .getSingleResult();
    }

    private InterviewSession persistSession(User user, InterviewSessionStatus status, Instant activityAt) {
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application);
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                // 상세 조회 테스트는 startedAt과 lastActivityAt을 같은 값으로 맞춰야
                // 자동 일시정지 전후 비교가 deterministic 하다.
                .startedAt(activityAt)
                .lastActivityAt(activityAt)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private void persistAnswer(
            InterviewSession session,
            InterviewQuestion question,
            int answerOrder,
            boolean skipped,
            String answerText
    ) {
        InterviewAnswer answer = InterviewAnswer.builder()
                .session(session)
                .question(question)
                .answerOrder(answerOrder)
                .answerText(answerText)
                .skipped(skipped)
                .build();
        entityManager.persist(answer);
        entityManager.flush();
    }
}
