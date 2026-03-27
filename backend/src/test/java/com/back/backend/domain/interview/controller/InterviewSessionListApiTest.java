package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.back.backend.support.TestFixtures;
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
    private TestFixtures fixtures;

    @Test
    void getSessions_returnsCurrentUsersSessionsWithActiveSessionFirst() throws Exception {
        User user = fixtures.createUser("session-list@example.com", "session-list");
        User otherUser = fixtures.createUser("session-list-other@example.com", "session-list-other");

        Application application = fixtures.createApplication(user, "history-app");
        Application otherApplication = fixtures.createApplication(otherUser, "history-other-app");

        InterviewQuestionSet activeSet = fixtures.createQuestionSet(user, application, "active-set", 3);
        InterviewQuestionSet completedSet = fixtures.createQuestionSet(user, application, "completed-set", 3);
        InterviewQuestionSet feedbackSet = fixtures.createQuestionSet(user, application, "feedback-set", 3);
        InterviewQuestionSet otherSet = fixtures.createQuestionSet(otherUser, otherApplication, "other-set", 3);

        fixtures.createInterviewSession(user, activeSet, InterviewSessionStatus.PAUSED,
                null, null, Instant.parse("2026-03-20T09:00:00Z"), null);
        fixtures.createInterviewSession(user, completedSet, InterviewSessionStatus.COMPLETED,
                null, null, Instant.parse("2026-03-24T09:00:00Z"), Instant.parse("2026-03-24T09:30:00Z"));
        fixtures.createInterviewSession(user, feedbackSet, InterviewSessionStatus.FEEDBACK_COMPLETED,
                84, "핵심 경험 설명은 좋았지만 답변 구조를 더 선명하게 다듬을 필요가 있습니다.",
                Instant.parse("2026-03-25T09:00:00Z"), Instant.parse("2026-03-25T09:35:00Z"));
        fixtures.createInterviewSession(otherUser, otherSet, InterviewSessionStatus.FEEDBACK_COMPLETED,
                91, "다른 사용자의 세션입니다.",
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
        User user = fixtures.createUser("session-empty@example.com", "session-empty");

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
}
