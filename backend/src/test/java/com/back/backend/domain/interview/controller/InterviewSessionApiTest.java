package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.interview.dto.request.StartInterviewSessionRequest;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewSessionRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewSessionApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-03-25T09:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Test
    void startSession_returns201AndCreatesInProgressSession() throws Exception {
        User user = persistUser("session-start@example.com", "session-start");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 3);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");
        persistQuestion(questionSet, 3, "세 번째 질문");

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
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.endedAt").value(nullValue()));

        entityManager.flush();
        entityManager.clear();

        assertThat(interviewSessionRepository.findAll())
                .singleElement()
                .satisfies(session -> {
                    assertThat(session.getQuestionSet().getId()).isEqualTo(questionSet.getId());
                    assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
                    assertThat(session.getStartedAt()).isNotNull();
                    assertThat(session.getEndedAt()).isNull();
                });
    }

    @Test
    void startSession_returns404WhenQuestionSetIsNotOwned() throws Exception {
        User owner = persistUser("session-owner@example.com", "session-owner");
        User otherUser = persistUser("session-other@example.com", "session-other");
        Application application = persistApplication(owner, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(owner, application, 3);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");
        persistQuestion(questionSet, 3, "세 번째 질문");

        mockMvc.perform(post("/api/v1/interview/sessions")
                        .with(authenticated(otherUser.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new StartInterviewSessionRequest(questionSet.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void startSession_returns409WhenInProgressSessionAlreadyExists() throws Exception {
        User user = persistUser("session-active@example.com", "session-active");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet existingSet = persistQuestionSet(user, application, 3);
        persistQuestion(existingSet, 1, "첫 번째 질문");
        persistQuestion(existingSet, 2, "두 번째 질문");
        persistQuestion(existingSet, 3, "세 번째 질문");
        persistSession(user, existingSet, InterviewSessionStatus.IN_PROGRESS);

        InterviewQuestionSet newSet = persistQuestionSet(user, application, 3);
        persistQuestion(newSet, 1, "새 질문 1");
        persistQuestion(newSet, 2, "새 질문 2");
        persistQuestion(newSet, 3, "새 질문 3");

        mockMvc.perform(post("/api/v1/interview/sessions")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new StartInterviewSessionRequest(newSet.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_ACTIVE.name()));
    }

    @Test
    void startSession_returns409WhenPausedSessionAlreadyExists() throws Exception {
        User user = persistUser("session-paused@example.com", "session-paused");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet existingSet = persistQuestionSet(user, application, 3);
        persistQuestion(existingSet, 1, "첫 번째 질문");
        persistQuestion(existingSet, 2, "두 번째 질문");
        persistQuestion(existingSet, 3, "세 번째 질문");
        persistSession(user, existingSet, InterviewSessionStatus.PAUSED);

        InterviewQuestionSet newSet = persistQuestionSet(user, application, 3);
        persistQuestion(newSet, 1, "새 질문 1");
        persistQuestion(newSet, 2, "새 질문 2");
        persistQuestion(newSet, 3, "새 질문 3");

        mockMvc.perform(post("/api/v1/interview/sessions")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new StartInterviewSessionRequest(newSet.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_SESSION_ALREADY_ACTIVE.name()));
    }

    @Test
    void startSession_returns400WhenQuestionCountIsOutOfRange() throws Exception {
        User user = persistUser("session-question-count@example.com", "session-question-count");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 2);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");

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

    private void persistSession(User user, InterviewQuestionSet questionSet, InterviewSessionStatus status) {
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(status)
                .startedAt(NOW)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
    }
}
