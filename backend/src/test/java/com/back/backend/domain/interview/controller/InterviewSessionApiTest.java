package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.interview.dto.request.StartInterviewSessionRequest;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.back.backend.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewSessionApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
    }

    @Test
    void startSession_returns201AndCreatesInProgressSession() throws Exception {
        User user = fixtures.createUser("session-start@example.com", "session-start");
        Application application = fixtures.createApplication(user, "application-title");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(user, application, 3);
        fixtures.createInterviewQuestion(questionSet, 1, "첫 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 2, "두 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new StartInterviewSessionRequest(questionSet.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionSetId").value(questionSet.getId()))
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andExpect(jsonPath("$.data.totalScore").value(nullValue()))
                .andExpect(jsonPath("$.data.summaryFeedback").value(nullValue()))
                .andExpect(jsonPath("$.data.startedAt").value(FIXED_NOW.toString()))
                .andExpect(jsonPath("$.data.endedAt").value(nullValue()));

        entityManager.flush();
        entityManager.clear();

        assertThat(interviewSessionRepository.findAll())
                .singleElement()
                .satisfies(session -> {
                    assertThat(session.getQuestionSet().getId()).isEqualTo(questionSet.getId());
                    assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
                    assertThat(session.getStartedAt()).isEqualTo(FIXED_NOW);
                    assertThat(session.getLastActivityAt()).isEqualTo(session.getStartedAt());
                    assertThat(session.getEndedAt()).isNull();
                });
    }

    @Test
    void startSession_returns404WhenQuestionSetIsNotOwned() throws Exception {
        User owner = fixtures.createUser("session-owner@example.com", "session-owner");
        User otherUser = fixtures.createUser("session-other@example.com", "session-other");
        Application application = fixtures.createApplication(owner, "application-title");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(owner, application, 3);
        fixtures.createInterviewQuestion(questionSet, 1, "첫 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 2, "두 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions")
                        .with(authenticated(otherUser.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new StartInterviewSessionRequest(questionSet.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void startSession_returns409WhenInProgressSessionAlreadyExists() throws Exception {
        User user = fixtures.createUser("session-active@example.com", "session-active");
        Application application = fixtures.createApplication(user, "application-title");
        InterviewQuestionSet existingSet = fixtures.createQuestionSet(user, application, 3);
        fixtures.createInterviewQuestion(existingSet, 1, "첫 번째 질문");
        fixtures.createInterviewQuestion(existingSet, 2, "두 번째 질문");
        fixtures.createInterviewQuestion(existingSet, 3, "세 번째 질문");
        fixtures.createInterviewSession(user, existingSet, InterviewSessionStatus.IN_PROGRESS, FIXED_NOW);

        InterviewQuestionSet newSet = fixtures.createQuestionSet(user, application, 3);
        fixtures.createInterviewQuestion(newSet, 1, "새 질문 1");
        fixtures.createInterviewQuestion(newSet, 2, "새 질문 2");
        fixtures.createInterviewQuestion(newSet, 3, "새 질문 3");

        mockMvc.perform(post("/api/v1/interview/sessions")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new StartInterviewSessionRequest(newSet.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_ACTIVE.name()));
    }

    @Test
    void startSession_returns409WhenPausedSessionAlreadyExists() throws Exception {
        User user = fixtures.createUser("session-paused@example.com", "session-paused");
        Application application = fixtures.createApplication(user, "application-title");
        InterviewQuestionSet existingSet = fixtures.createQuestionSet(user, application, 3);
        fixtures.createInterviewQuestion(existingSet, 1, "첫 번째 질문");
        fixtures.createInterviewQuestion(existingSet, 2, "두 번째 질문");
        fixtures.createInterviewQuestion(existingSet, 3, "세 번째 질문");
        fixtures.createInterviewSession(user, existingSet, InterviewSessionStatus.PAUSED, FIXED_NOW);

        InterviewQuestionSet newSet = fixtures.createQuestionSet(user, application, 3);
        fixtures.createInterviewQuestion(newSet, 1, "새 질문 1");
        fixtures.createInterviewQuestion(newSet, 2, "새 질문 2");
        fixtures.createInterviewQuestion(newSet, 3, "새 질문 3");

        mockMvc.perform(post("/api/v1/interview/sessions")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new StartInterviewSessionRequest(newSet.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_ACTIVE.name()));
    }

    @Test
    void startSession_returns400WhenQuestionCountIsOutOfRange() throws Exception {
        User user = fixtures.createUser("session-question-count@example.com", "session-question-count");
        Application application = fixtures.createApplication(user, "application-title");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(user, application, 2);
        fixtures.createInterviewQuestion(questionSet, 1, "첫 번째 질문");
        fixtures.createInterviewQuestion(questionSet, 2, "두 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new StartInterviewSessionRequest(questionSet.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("questionCount"));
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }
}
