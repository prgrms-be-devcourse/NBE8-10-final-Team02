package com.back.backend.domain.interview.controller;

import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.interview.dto.request.CreateQuestionSetRequest;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.service.AsyncInterviewQuestionsGenerateService;
import com.back.backend.domain.interview.service.InterviewQuestionSetJobStatus;
import com.back.backend.domain.interview.service.InterviewQuestionSetJobStore;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewQuestionSetGenerateApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private AsyncInterviewQuestionsGenerateService asyncInterviewQuestionsGenerateService;

    @MockitoBean
    private InterviewQuestionSetJobStore interviewQuestionSetJobStore;

    // ── POST /question-sets ──────────────────────────────────────────────────

    @Test
    void createQuestionSet_returns202WithJobId() throws Exception {
        User user = persistUser("generate@example.com", "generate-tester");
        Application application = persistApplication(user);

        String jobId = "test-job-id-uuid";
        given(asyncInterviewQuestionsGenerateService.submitAsync(
                eq(user.getId()), eq(application.getId()), any(), anyInt(), any(), any()
        )).willReturn(jobId);

        given(interviewQuestionSetJobStore.get(user.getId(), jobId))
                .willReturn(Optional.of(new InterviewQuestionSetJobStore.JobData(
                        jobId, InterviewQuestionSetJobStatus.PENDING, null, Instant.now(), null, null
                )));

        CreateQuestionSetRequest request = new CreateQuestionSetRequest(
                application.getId(), "면접 질문 세트", 5, "medium",
                List.of("technical_cs", "behavioral")
        );

        mockMvc.perform(post("/api/v1/interview/question-sets")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.questionSetId").doesNotExist());
    }

    @Test
    void createQuestionSet_returns401WhenUnauthenticated() throws Exception {
        CreateQuestionSetRequest request = new CreateQuestionSetRequest(
                1L, "제목", 3, "medium", List.of("technical_cs")
        );

        mockMvc.perform(post("/api/v1/interview/question-sets")
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void createQuestionSet_returns404WhenApplicationNotOwned() throws Exception {
        User owner = persistUser("owner@example.com", "owner");
        User other = persistUser("other@example.com", "other");
        Application application = persistApplication(owner);

        willThrow(new ServiceException(
                ErrorCode.APPLICATION_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "지원 준비를 찾을 수 없습니다."
        )).given(asyncInterviewQuestionsGenerateService)
                .validateOwnership(other.getId(), application.getId());

        CreateQuestionSetRequest request = new CreateQuestionSetRequest(
                application.getId(), "제목", 3, "medium", List.of("technical_cs")
        );

        mockMvc.perform(post("/api/v1/interview/question-sets")
                        .with(authenticated(other.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_NOT_FOUND.name()));
    }

    // ── GET /question-sets/status/{jobId} ────────────────────────────────────

    @Test
    void getQuestionSetJobStatus_returns200WithCompletedJob() throws Exception {
        User user = persistUser("status@example.com", "status-tester");
        String jobId = "completed-job-id";
        long questionSetId = 42L;

        given(interviewQuestionSetJobStore.get(user.getId(), jobId))
                .willReturn(Optional.of(new InterviewQuestionSetJobStore.JobData(
                        jobId, InterviewQuestionSetJobStatus.COMPLETED, questionSetId,
                        Instant.now().minusSeconds(10), Instant.now(), null
                )));

        mockMvc.perform(get("/api/v1/interview/question-sets/status/{jobId}", jobId)
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.questionSetId").value(questionSetId));
    }

    @Test
    void getQuestionSetJobStatus_returns200WithNullWhenJobNotFound() throws Exception {
        User user = persistUser("status-null@example.com", "status-null");

        given(interviewQuestionSetJobStore.get(anyLong(), any()))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/interview/question-sets/status/{jobId}", "unknown-job-id")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getQuestionSetJobStatus_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/interview/question-sets/status/{jobId}", "some-job-id"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /question-sets ────────────────────────────────────────────────────

    @Test
    void getQuestionSets_returns200WithList() throws Exception {
        User user = persistUser("list@example.com", "list-tester");
        Application application = persistApplication(user);
        persistQuestionSet(user, application, "세트1", 3);
        persistQuestionSet(user, application, "세트2", 5);

        mockMvc.perform(get("/api/v1/interview/question-sets")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ── GET /question-sets/{questionSetId} ───────────────────────────────────

    @Test
    void getQuestionSet_returns200WithDetail() throws Exception {
        User user = persistUser("detail@example.com", "detail-tester");
        Application application = persistApplication(user);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "상세 세트", 2);
        persistQuestion(questionSet, 1, "첫 번째 질문", InterviewQuestionType.TECHNICAL_CS);
        persistQuestion(questionSet, 2, "두 번째 질문", InterviewQuestionType.PROJECT);

        mockMvc.perform(get("/api/v1/interview/question-sets/{questionSetId}", questionSet.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("상세 세트"))
                .andExpect(jsonPath("$.data.questionCount").value(2))
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[0].questionText").value("첫 번째 질문"))
                .andExpect(jsonPath("$.data.questions[0].questionType").value("technical_cs"))
                .andExpect(jsonPath("$.data.questions[1].questionText").value("두 번째 질문"));
    }

    @Test
    void getQuestionSet_returns404WhenNotOwned() throws Exception {
        User owner = persistUser("qs-owner@example.com", "qs-owner");
        User other = persistUser("qs-other@example.com", "qs-other");
        Application application = persistApplication(owner);
        InterviewQuestionSet questionSet = persistQuestionSet(owner, application, "세트", 1);

        mockMvc.perform(get("/api/v1/interview/question-sets/{questionSetId}", questionSet.getId())
                        .with(authenticated(other.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_QUESTION_SET_NOT_FOUND.name()));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId, AuthorityUtils.createAuthorityList("ROLE_USER")
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

    private Application persistApplication(User user) {
        Application application = Application.builder()
                .user(user)
                .applicationTitle("카카오 백엔드")
                .companyName("카카오")
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(ApplicationStatus.DRAFT)
                .build();
        entityManager.persist(application);
        entityManager.flush();
        return application;
    }

    private InterviewQuestionSet persistQuestionSet(User user, Application application,
                                                     String title, int questionCount) {
        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title(title)
                .questionCount(questionCount)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionTypes(new String[]{"technical_cs", "project"})
                .build();
        entityManager.persist(questionSet);
        entityManager.flush();
        return questionSet;
    }

    private InterviewQuestion persistQuestion(InterviewQuestionSet questionSet, int order,
                                               String questionText, InterviewQuestionType type) {
        InterviewQuestion question = InterviewQuestion.builder()
                .questionSet(questionSet)
                .questionOrder(order)
                .questionType(type)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .questionText(questionText)
                .build();
        entityManager.persist(question);
        entityManager.flush();
        return question;
    }
}
