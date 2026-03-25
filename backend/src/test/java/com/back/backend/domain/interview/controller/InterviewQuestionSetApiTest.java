package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.interview.dto.request.AddInterviewQuestionRequest;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewQuestionSetApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-03-24T09:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Test
    void addQuestion_returns201AndAppendsQuestionOrder() throws Exception {
        User user = persistUser("interview-add@example.com", "interview-add");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 2);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");

        mockMvc.perform(post("/api/v1/interview/question-sets/{questionSetId}/questions", questionSet.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new AddInterviewQuestionRequest(
                                "  이 회사에 지원한 이유를 설명해주세요.  ",
                                "behavioral",
                                "medium"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionOrder").value(3))
                .andExpect(jsonPath("$.data.questionType").value("behavioral"))
                .andExpect(jsonPath("$.data.difficultyLevel").value("medium"))
                .andExpect(jsonPath("$.data.questionText").value("이 회사에 지원한 이유를 설명해주세요."))
                .andExpect(jsonPath("$.data.parentQuestionId").value(nullValue()))
                .andExpect(jsonPath("$.data.sourceApplicationQuestionId").value(nullValue()));

        entityManager.flush();
        entityManager.clear();

        InterviewQuestion savedQuestion = interviewQuestionRepository.findTopByQuestionSetIdOrderByQuestionOrderDesc(questionSet.getId())
                .orElseThrow();
        InterviewQuestionSet refreshedQuestionSet = entityManager.find(InterviewQuestionSet.class, questionSet.getId());

        assertThat(savedQuestion.getQuestionOrder()).isEqualTo(3);
        assertThat(savedQuestion.getQuestionText()).isEqualTo("이 회사에 지원한 이유를 설명해주세요.");
        assertThat(refreshedQuestionSet.getQuestionCount()).isEqualTo(3);
    }

    @Test
    void addQuestion_returns404WhenQuestionSetIsNotOwned() throws Exception {
        User owner = persistUser("interview-owner@example.com", "owner");
        User otherUser = persistUser("interview-other@example.com", "other");
        Application application = persistApplication(owner, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(owner, application, 1);

        mockMvc.perform(post("/api/v1/interview/question-sets/{questionSetId}/questions", questionSet.getId())
                        .with(authenticated(otherUser.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new AddInterviewQuestionRequest(
                                "질문",
                                "behavioral",
                                "medium"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void addQuestion_returns409WhenQuestionSetAlreadyStarted() throws Exception {
        User user = persistUser("interview-locked@example.com", "locked");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 1);
        persistStartedSession(user, questionSet);

        mockMvc.perform(post("/api/v1/interview/question-sets/{questionSetId}/questions", questionSet.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new AddInterviewQuestionRequest(
                                "질문",
                                "behavioral",
                                "medium"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_QUESTION_SET_NOT_EDITABLE.name()));
    }

    @Test
    void addQuestion_returns400WhenRequestIsInvalid() throws Exception {
        User user = persistUser("interview-invalid@example.com", "invalid");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 1);

        mockMvc.perform(post("/api/v1/interview/question-sets/{questionSetId}/questions", questionSet.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new AddInterviewQuestionRequest(
                                "   ",
                                "follow_up",
                                "invalid"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("questionText"));
    }

    @Test
    void deleteQuestion_returns204AndReordersRemainingQuestions() throws Exception {
        User user = persistUser("interview-delete@example.com", "delete-owner");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 3);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        InterviewQuestion secondQuestion = persistQuestion(questionSet, 2, "두 번째 질문");
        persistQuestion(questionSet, 3, "세 번째 질문");

        mockMvc.perform(delete("/api/v1/interview/question-sets/{questionSetId}/questions/{questionId}", questionSet.getId(), secondQuestion.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        InterviewQuestionSet refreshedQuestionSet = entityManager.find(InterviewQuestionSet.class, questionSet.getId());
        assertThat(refreshedQuestionSet.getQuestionCount()).isEqualTo(2);

        assertThat(interviewQuestionRepository.findAllByQuestionSetIdOrderByQuestionOrderAsc(questionSet.getId()))
                .extracting(InterviewQuestion::getQuestionOrder, InterviewQuestion::getQuestionText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "첫 번째 질문"),
                        org.assertj.core.groups.Tuple.tuple(2, "세 번째 질문")
                );
    }

    @Test
    void deleteQuestion_returns400WhenDeletingLastRemainingQuestion() throws Exception {
        User user = persistUser("interview-delete-last@example.com", "delete-last");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 1);
        InterviewQuestion question = persistQuestion(questionSet, 1, "유일한 질문");

        mockMvc.perform(delete("/api/v1/interview/question-sets/{questionSetId}/questions/{questionId}", questionSet.getId(), question.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("questionId"));
    }

    @Test
    void deleteQuestion_returns404WhenQuestionIsMissing() throws Exception {
        User user = persistUser("interview-delete-missing@example.com", "delete-missing");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 2);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");

        mockMvc.perform(delete("/api/v1/interview/question-sets/{questionSetId}/questions/{questionId}", questionSet.getId(), 999999L)
                        .with(authenticated(user.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void deleteQuestion_returns409WhenQuestionSetAlreadyStarted() throws Exception {
        User user = persistUser("interview-delete-locked@example.com", "delete-locked");
        Application application = persistApplication(user, "application-title");
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, 2);
        InterviewQuestion question = persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");
        persistStartedSession(user, questionSet);

        mockMvc.perform(delete("/api/v1/interview/question-sets/{questionSetId}/questions/{questionId}", questionSet.getId(), question.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_QUESTION_SET_NOT_EDITABLE.name()));
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

    private void persistStartedSession(User user, InterviewQuestionSet questionSet) {
        InterviewSession session = InterviewSession.builder()
                .user(user)
                .questionSet(questionSet)
                .status(InterviewSessionStatus.IN_PROGRESS)
                .startedAt(NOW)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
    }
}
