package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewSessionTransitionApiTest extends ApiTestBase {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestFixtures fixtures;

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
    }

    @Test
    void pauseSession_returns200AndChangesStatusToPaused() throws Exception {
        User user = fixtures.createUser("pause-success@example.com", "pause-success");
        InterviewSession session = createSessionWithQuestions(user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/pause", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.data.status").value("paused"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = entityManager.find(InterviewSession.class, session.getId());
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.PAUSED);
        assertThat(refreshedSession.getLastActivityAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void pauseSession_returns404WhenSessionIsNotOwned() throws Exception {
        User owner = fixtures.createUser("pause-owner@example.com", "pause-owner");
        User otherUser = fixtures.createUser("pause-other@example.com", "pause-other");
        InterviewSession session = createSessionWithQuestions(owner, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/pause", session.getId())
                        .with(authenticated(otherUser.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void pauseSession_returns409WhenStatusTransitionIsInvalid() throws Exception {
        User user = fixtures.createUser("pause-conflict@example.com", "pause-conflict");
        InterviewSession session = createSessionWithQuestions(user, InterviewSessionStatus.PAUSED, FIXED_NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/pause", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_STATUS_CONFLICT.name()));
    }

    @Test
    void pauseSession_returns409WhenSessionAlreadyCompleted() throws Exception {
        User user = fixtures.createUser("pause-completed@example.com", "pause-completed");
        InterviewSession session = createSessionWithQuestions(user, InterviewSessionStatus.COMPLETED, FIXED_NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/pause", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED.name()));
    }

    @Test
    void resumeSession_returns200AndUpdatesLastActivityAt() throws Exception {
        User user = fixtures.createUser("resume-success@example.com", "resume-success");
        Instant previousActivityAt = FIXED_NOW.minus(Duration.ofMinutes(10));
        InterviewSession session = createSessionWithQuestions(user, InterviewSessionStatus.PAUSED, previousActivityAt);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/resume", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = entityManager.find(InterviewSession.class, session.getId());
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(refreshedSession.getLastActivityAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void resumeSession_returns200WhenExpiredInProgressSessionIsNormalizedFirst() throws Exception {
        User user = fixtures.createUser("resume-expired@example.com", "resume-expired");
        InterviewSession session = createSessionWithQuestions(
                user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW.minus(Duration.ofMinutes(31)));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/resume", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_progress"));

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = entityManager.find(InterviewSession.class, session.getId());
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(refreshedSession.getLastActivityAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void resumeSession_returns409WhenSessionIsAlreadyActive() throws Exception {
        User user = fixtures.createUser("resume-conflict@example.com", "resume-conflict");
        InterviewSession session = createSessionWithQuestions(user, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/resume", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_STATUS_CONFLICT.name()));
    }

    @Test
    void resumeSession_returns409WhenSessionAlreadyCompleted() throws Exception {
        User user = fixtures.createUser("resume-completed@example.com", "resume-completed");
        InterviewSession session = createSessionWithQuestions(
                user, InterviewSessionStatus.FEEDBACK_COMPLETED, FIXED_NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/resume", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED.name()));
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
}
