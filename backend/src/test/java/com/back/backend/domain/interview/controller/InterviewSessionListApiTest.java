package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewSessionListApiTest extends ApiTestBase {

    @Autowired
    private EntityManager entityManager;

    @Test
    void getSessions_returnsCurrentUsersSessionsWithActiveSessionFirst() throws Exception {
        User user = persistUser("session-list@example.com", "session-list");
        User otherUser = persistUser("session-list-other@example.com", "session-list-other");

        Application application = persistApplication(user, "history-app");
        Application otherApplication = persistApplication(otherUser, "history-other-app");

        InterviewQuestionSet activeSet = persistQuestionSet(user, application, "active-set");
        InterviewQuestionSet completedSet = persistQuestionSet(user, application, "completed-set");
        InterviewQuestionSet feedbackSet = persistQuestionSet(user, application, "feedback-set");
        InterviewQuestionSet otherSet = persistQuestionSet(otherUser, otherApplication, "other-set");

        persistSession(user, activeSet, InterviewSessionStatus.PAUSED, null, null,
                Instant.parse("2026-03-20T09:00:00Z"), null);
        persistSession(user, completedSet, InterviewSessionStatus.COMPLETED, null, null,
                Instant.parse("2026-03-24T09:00:00Z"), Instant.parse("2026-03-24T09:30:00Z"));
        persistSession(user, feedbackSet, InterviewSessionStatus.FEEDBACK_COMPLETED, 84,
                "핵심 경험 설명은 좋았지만 답변 구조를 더 선명하게 다듬을 필요가 있습니다.",
                Instant.parse("2026-03-25T09:00:00Z"), Instant.parse("2026-03-25T09:35:00Z"));
        persistSession(otherUser, otherSet, InterviewSessionStatus.FEEDBACK_COMPLETED, 91,
                "다른 사용자의 세션입니다.",
                Instant.parse("2026-03-26T09:00:00Z"), Instant.parse("2026-03-26T09:20:00Z"));

        mockMvc.perform(get("/api/v1/interview/sessions")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].questionSetId").value(activeSet.getId()))
                .andExpect(jsonPath("$.data[0].status").value("paused"))
                .andExpect(jsonPath("$.data[0].totalScore").value(nullValue()))
                .andExpect(jsonPath("$.data[1].questionSetId").value(feedbackSet.getId()))
                .andExpect(jsonPath("$.data[1].status").value("feedback_completed"))
                .andExpect(jsonPath("$.data[1].totalScore").value(84))
                .andExpect(jsonPath("$.data[1].summaryFeedback")
                        .value("핵심 경험 설명은 좋았지만 답변 구조를 더 선명하게 다듬을 필요가 있습니다."))
                .andExpect(jsonPath("$.data[2].questionSetId").value(completedSet.getId()))
                .andExpect(jsonPath("$.data[2].status").value("completed"))
                .andExpect(jsonPath("$.data[2].totalScore").value(nullValue()));
    }

    @Test
    void getSessions_returnsEmptyArrayWhenCurrentUserHasNoSessions() throws Exception {
        User user = persistUser("session-empty@example.com", "session-empty");

        mockMvc.perform(get("/api/v1/interview/sessions")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
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

    private InterviewQuestionSet persistQuestionSet(User user, Application application, String title) {
        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title(title)
                .questionCount(3)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"behavioral"})
                .build();
        entityManager.persist(questionSet);
        entityManager.flush();
        return questionSet;
    }

    private void persistSession(
            User user,
            InterviewQuestionSet questionSet,
            InterviewSessionStatus status,
            Integer totalScore,
            String summaryFeedback,
            Instant startedAt,
            Instant endedAt
    ) {
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .totalScore(totalScore)
                .summaryFeedback(summaryFeedback)
                .startedAt(startedAt)
                .lastActivityAt(startedAt)
                .endedAt(endedAt)
                .build();
        entityManager.persist(session);
        entityManager.flush();
    }
}
