package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.interview.entity.DifficultyLevel;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewSessionTransitionApiTest extends ApiTestBase {

    private static final Instant NOW = Instant.now();

    @Autowired
    private EntityManager entityManager;

    @Test
    void pauseSession_returns200AndChangesStatusToPaused() throws Exception {
        User user = persistUser("pause-success@example.com", "pause-success");
        InterviewSession session = persistSession(user, InterviewSessionStatus.IN_PROGRESS, NOW);

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
        //assertThat(refreshedSession.getLastActivityAt()).isEqualTo(NOW);
    }

    @Test
    void pauseSession_returns404WhenSessionIsNotOwned() throws Exception {
        User owner = persistUser("pause-owner@example.com", "pause-owner");
        User otherUser = persistUser("pause-other@example.com", "pause-other");
        InterviewSession session = persistSession(owner, InterviewSessionStatus.IN_PROGRESS, NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/pause", session.getId())
                        .with(authenticated(otherUser.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void pauseSession_returns409WhenStatusTransitionIsInvalid() throws Exception {
        User user = persistUser("pause-conflict@example.com", "pause-conflict");
        InterviewSession session = persistSession(user, InterviewSessionStatus.PAUSED, NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/pause", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_STATUS_CONFLICT.name()));
    }

    @Test
    void pauseSession_returns409WhenSessionAlreadyCompleted() throws Exception {
        User user = persistUser("pause-completed@example.com", "pause-completed");
        InterviewSession session = persistSession(user, InterviewSessionStatus.COMPLETED, NOW);

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/pause", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED.name()));
    }

    @Test
    void resumeSession_returns200AndUpdatesLastActivityAt() throws Exception {
        User user = persistUser("resume-success@example.com", "resume-success");
        Instant previousActivityAt = Instant.now().minus(Duration.ofMinutes(10));
        InterviewSession session = persistSession(user, InterviewSessionStatus.PAUSED, previousActivityAt);

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
        assertThat(refreshedSession.getLastActivityAt()).isAfter(previousActivityAt);
    }

    @Test
    void resumeSession_returns200WhenExpiredInProgressSessionIsNormalizedFirst() throws Exception {
        User user = persistUser("resume-expired@example.com", "resume-expired");
        // auto-pause 경계를 넘긴 in_progress 세션을 바로 resume했을 때,
        // 내부적으로 paused 정규화를 거친 뒤 같은 요청에서 재개되는 흐름을 검증한다.
        InterviewSession session = persistSession(user, InterviewSessionStatus.IN_PROGRESS, Instant.now().minus(Duration.ofMinutes(31)));

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/resume", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_progress"));

        entityManager.flush();
        entityManager.clear();

        InterviewSession refreshedSession = entityManager.find(InterviewSession.class, session.getId());
        assertThat(refreshedSession.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(refreshedSession.getLastActivityAt()).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
    }

    @Test
    void resumeSession_returns409WhenSessionIsAlreadyActive() throws Exception {
        User user = persistUser("resume-conflict@example.com", "resume-conflict");
        InterviewSession session = persistSession(user, InterviewSessionStatus.IN_PROGRESS, Instant.now());

        mockMvc.perform(post("/api/v1/interview/sessions/{sessionId}/resume", session.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_STATUS_CONFLICT.name()));
    }

    @Test
    void resumeSession_returns409WhenSessionAlreadyCompleted() throws Exception {
        User user = persistUser("resume-completed@example.com", "resume-completed");
        InterviewSession session = persistSession(user, InterviewSessionStatus.FEEDBACK_COMPLETED, NOW);

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

    private InterviewSession persistSession(User user, InterviewSessionStatus status, Instant activityAt) {
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application);
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(activityAt)
                .lastActivityAt(activityAt)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }
}
