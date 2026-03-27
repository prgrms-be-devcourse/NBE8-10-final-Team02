package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.back.backend.support.TestFixtures;
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

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private InterviewQuestionRepository questionRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
    }

    @Test
    void getSessionDetail_returns200ForPausedSession() throws Exception {
        User user = fixtures.createUser("detail-paused@example.com", "detail-paused");
        InterviewSession session = createSessionWithQuestions(user, InterviewSessionStatus.PAUSED, FIXED_NOW);
        InterviewQuestion firstQuestion = findQuestion(session, 1);
        InterviewQuestion secondQuestion = findQuestion(session, 2);
        fixtures.createInterviewAnswer(session, firstQuestion, 1,
                "첫 번째 답변입니다. 충분히 긴 답변으로 조건을 만족합니다.");

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
        User user = fixtures.createUser("detail-in-progress@example.com", "detail-in-progress");
        InterviewSession session = createSessionWithQuestions(user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);
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
        User owner = fixtures.createUser("detail-owner@example.com", "detail-owner");
        User otherUser = fixtures.createUser("detail-other@example.com", "detail-other");
        InterviewSession session = createSessionWithQuestions(owner, InterviewSessionStatus.PAUSED, FIXED_NOW);

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(otherUser.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void getSessionDetail_normalizesExpiredInProgressSessionToPaused() throws Exception {
        User user = fixtures.createUser("detail-expired@example.com", "detail-expired");
        Instant expiredActivityAt = FIXED_NOW.minus(Duration.ofMinutes(31));
        InterviewSession session = createSessionWithQuestions(
                user, InterviewSessionStatus.IN_PROGRESS, expiredActivityAt);

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
        User user = fixtures.createUser("detail-complete@example.com", "detail-complete");
        InterviewSession session = createSessionWithQuestions(user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);
        InterviewQuestion firstQuestion = findQuestion(session, 1);
        InterviewQuestion secondQuestion = findQuestion(session, 2);
        InterviewQuestion thirdQuestion = findQuestion(session, 3);
        fixtures.createInterviewAnswer(session, firstQuestion, 1,
                "첫 번째 답변입니다. 충분히 긴 답변으로 조건을 만족합니다.");
        fixtures.createInterviewAnswer(session, secondQuestion, 2,
                "두 번째 답변입니다. 충분히 긴 답변으로 조건을 만족합니다.");
        fixtures.createSkippedAnswer(session, thirdQuestion, 3);

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentQuestion").value(nullValue()))
                .andExpect(jsonPath("$.data.answeredQuestionCount").value(3))
                .andExpect(jsonPath("$.data.remainingQuestionCount").value(0))
                .andExpect(jsonPath("$.data.resumeAvailable").value(false));
    }

    @Test
    void getSessionDetail_returns200ForCompletedSessionHistoryBasicInfo() throws Exception {
        User user = fixtures.createUser("detail-history-completed@example.com", "detail-history-completed");
        Instant startedAt = FIXED_NOW.minus(Duration.ofMinutes(20));
        Instant endedAt = FIXED_NOW.minus(Duration.ofMinutes(5));
        InterviewSession session = createTerminalSessionWithQuestions(
                user, InterviewSessionStatus.COMPLETED, startedAt, endedAt);
        InterviewQuestion firstQuestion = findQuestion(session, 1);
        InterviewQuestion secondQuestion = findQuestion(session, 2);
        InterviewQuestion thirdQuestion = findQuestion(session, 3);
        fixtures.createInterviewAnswer(session, firstQuestion, 1,
                "첫 번째 답변입니다. 충분히 긴 답변으로 조건을 만족합니다.");
        fixtures.createInterviewAnswer(session, secondQuestion, 2,
                "두 번째 답변입니다. 충분히 긴 답변으로 조건을 만족합니다.");
        fixtures.createSkippedAnswer(session, thirdQuestion, 3);

        mockMvc.perform(get("/api/v1/interview/sessions/{sessionId}", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.currentQuestion").value(nullValue()))
                .andExpect(jsonPath("$.data.totalQuestionCount").value(3))
                .andExpect(jsonPath("$.data.answeredQuestionCount").value(3))
                .andExpect(jsonPath("$.data.remainingQuestionCount").value(0))
                .andExpect(jsonPath("$.data.resumeAvailable").value(false))
                .andExpect(jsonPath("$.data.startedAt").value(startedAt.toString()))
                .andExpect(jsonPath("$.data.endedAt").value(endedAt.toString()));
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }

    /** 3개의 질문을 포함한 세션을 한 번에 생성하는 내부 헬퍼. */
    private InterviewSession createSessionWithQuestions(User user, InterviewSessionStatus status, Instant activityAt) {
        Application application = fixtures.createApplication(user, "application-title");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(user, application, 3);
        fixtures.createInterviewQuestion(questionSet, 1, "첫 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 2, "두 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 3, "세 번째 질문");
        return fixtures.createInterviewSession(user, questionSet, status, activityAt);
    }

    private InterviewSession createTerminalSessionWithQuestions(User user, InterviewSessionStatus status,
                                                                  Instant startedAt, Instant endedAt) {
        Application application = fixtures.createApplication(user, "application-title");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(user, application, 3);
        fixtures.createInterviewQuestion(questionSet, 1, "첫 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 2, "두 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 3, "세 번째 질문");
        return fixtures.createTerminalSession(user, questionSet, status, startedAt, endedAt);
    }

    /** 세션의 questionSet에서 특정 순서의 질문을 조회한다. */
    private InterviewQuestion findQuestion(InterviewSession session, int questionOrder) {
        return questionRepository
                .findAllByQuestionSetIdOrderByQuestionOrderAsc(session.getQuestionSet().getId())
                .stream()
                .filter(q -> q.getQuestionOrder() == questionOrder)
                .findFirst()
                .orElseThrow();
    }
}
