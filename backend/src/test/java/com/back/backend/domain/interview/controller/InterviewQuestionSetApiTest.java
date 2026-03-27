package com.back.backend.domain.interview.controller;

import com.back.backend.domain.ai.client.AiClientException;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.interview.dto.request.AddInterviewQuestionRequest;
import com.back.backend.domain.interview.dto.request.CreateInterviewQuestionSetRequest;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.interview.repository.InterviewQuestionSetRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewQuestionSetApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2026-03-24T09:00:00Z");
    private static final String TEMPLATE_ID = "ai.interview.questions.generate.v1";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private InterviewQuestionSetRepository interviewQuestionSetRepository;

    @MockitoBean
    private AiPipeline aiPipeline;

    @Test
    void createQuestionSet_returns201AndPersistsGeneratedQuestions() throws Exception {
        User user = persistUser("interview-generate@example.com", "interview-generate");
        Application application = persistApplication(user, "네이버 백엔드", ApplicationStatus.DRAFT);
        persistApplicationQuestion(application, 1, "지원 동기를 설명해주세요.", "포트폴리오와 자소서 생성 흐름을 연결한 경험이 있습니다.");
        persistDocument(user, application, "Spring Boot, OAuth2, PostgreSQL 기반 프로젝트 경험");

        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "questions": [
                            {
                              "questionOrder": 1,
                              "questionType": "project",
                              "difficultyLevel": "medium",
                              "questionText": "프로젝트에서 가장 어려웠던 구현 과제를 설명해주세요.",
                              "sourceApplicationQuestionOrder": 1,
                              "parentQuestionOrder": null
                            },
                            {
                              "questionOrder": 2,
                              "questionType": "follow_up",
                              "difficultyLevel": "medium",
                              "questionText": "그때 어떤 근거로 해결 방안을 선택했나요?",
                              "sourceApplicationQuestionOrder": null,
                              "parentQuestionOrder": 1
                            }
                          ],
                          "qualityFlags": []
                        }
                        """));

        mockMvc.perform(post("/api/v1/interview/question-sets")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new CreateInterviewQuestionSetRequest(
                                application.getId(),
                                "네이버 백엔드 예상 질문 세트",
                                2,
                                "medium",
                                List.of("project", "follow_up")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(application.getId()))
                .andExpect(jsonPath("$.data.title").value("네이버 백엔드 예상 질문 세트"))
                .andExpect(jsonPath("$.data.questionCount").value(2))
                .andExpect(jsonPath("$.data.difficultyLevel").value("medium"));

        entityManager.flush();
        entityManager.clear();

        InterviewQuestionSet savedQuestionSet = interviewQuestionSetRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .findFirst()
                .orElseThrow();
        List<InterviewQuestion> savedQuestions =
                interviewQuestionRepository.findAllByQuestionSetIdOrderByQuestionOrderAsc(savedQuestionSet.getId());

        assertThat(savedQuestionSet.getTitle()).isEqualTo("네이버 백엔드 예상 질문 세트");
        assertThat(savedQuestionSet.getQuestionCount()).isEqualTo(2);
        assertThat(savedQuestions).hasSize(2);
        assertThat(savedQuestions.get(0).getSourceApplicationQuestion().getQuestionOrder()).isEqualTo(1);
        assertThat(savedQuestions.get(1).getParentQuestion().getId()).isEqualTo(savedQuestions.get(0).getId());
    }

    @Test
    void createQuestionSet_returns404WhenApplicationIsNotOwned() throws Exception {
        User owner = persistUser("interview-owner@example.com", "owner");
        User otherUser = persistUser("interview-other@example.com", "other");
        Application application = persistApplication(owner, "카카오 백엔드", ApplicationStatus.DRAFT);
        persistApplicationQuestion(application, 1, "지원 동기", "답변");
        persistDocument(owner, application, "문서 요약");

        mockMvc.perform(post("/api/v1/interview/question-sets")
                        .with(authenticated(otherUser.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new CreateInterviewQuestionSetRequest(
                                application.getId(),
                                null,
                                2,
                                "medium",
                                List.of("project")
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_NOT_FOUND.name()));
    }

    @Test
    void createQuestionSet_returns409WhenApplicationIsNotReady() throws Exception {
        User user = persistUser("interview-not-ready@example.com", "not-ready");
        Application application = persistApplication(user, "라인 백엔드", ApplicationStatus.DRAFT);

        mockMvc.perform(post("/api/v1/interview/question-sets")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new CreateInterviewQuestionSetRequest(
                                application.getId(),
                                null,
                                2,
                                "medium",
                                List.of("project")
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_STATUS_CONFLICT.name()));
    }

    @Test
    void createQuestionSet_returns502WhenAiResultIsInvalid() throws Exception {
        User user = persistUser("interview-invalid-ai@example.com", "invalid-ai");
        Application application = persistApplication(user, "토스 백엔드", ApplicationStatus.DRAFT);
        persistApplicationQuestion(application, 1, "지원 동기", "충분한 답변입니다.");
        persistDocument(user, application, "문서 요약");

        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willReturn(OBJECT_MAPPER.readTree("""
                        {
                          "questions": [
                            {
                              "questionOrder": 1,
                              "questionType": "project",
                              "difficultyLevel": "medium",
                              "questionText": "질문 하나만 생성되었습니다.",
                              "sourceApplicationQuestionOrder": 1,
                              "parentQuestionOrder": null
                            }
                          ],
                          "qualityFlags": []
                        }
                        """));

        mockMvc.perform(post("/api/v1/interview/question-sets")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new CreateInterviewQuestionSetRequest(
                                application.getId(),
                                null,
                                2,
                                "medium",
                                List.of("project")
                        ))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_QUESTION_RESULT_INVALID.name()));
    }

    @Test
    void createQuestionSet_returns503WhenAiProviderIsUnavailable() throws Exception {
        User user = persistUser("interview-ai-down@example.com", "ai-down");
        Application application = persistApplication(user, "배민 백엔드", ApplicationStatus.DRAFT);
        persistApplicationQuestion(application, 1, "지원 동기", "충분한 답변입니다.");
        persistDocument(user, application, "문서 요약");

        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
                .willThrow(new AiClientException(
                        AiProvider.GEMINI,
                        ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                        "ai unavailable"
                ));

        mockMvc.perform(post("/api/v1/interview/question-sets")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new CreateInterviewQuestionSetRequest(
                                application.getId(),
                                null,
                                2,
                                "medium",
                                List.of("project")
                        ))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE.name()));
    }

    @Test
    void getQuestionSets_returns200WithOwnedQuestionSets() throws Exception {
        User user = persistUser("interview-list@example.com", "list-user");
        Application application = persistApplication(user, "네이버 백엔드", ApplicationStatus.READY);
        InterviewQuestionSet firstQuestionSet = persistQuestionSet(user, application, "첫 번째 세트", 1);
        InterviewQuestionSet secondQuestionSet = persistQuestionSet(user, application, "두 번째 세트", 1);

        mockMvc.perform(get("/api/v1/interview/question-sets")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].questionSetId", containsInAnyOrder(
                        firstQuestionSet.getId().intValue(),
                        secondQuestionSet.getId().intValue()
                )));
    }

    @Test
    void getQuestionSetDetail_returns200WithOrderedQuestions() throws Exception {
        User user = persistUser("interview-detail@example.com", "detail-user");
        Application application = persistApplication(user, "카카오 백엔드", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "상세 세트", 2);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");

        mockMvc.perform(get("/api/v1/interview/question-sets/{questionSetId}", questionSet.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionSetId").value(questionSet.getId()))
                .andExpect(jsonPath("$.data.title").value("상세 세트"))
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[0].questionOrder").value(1))
                .andExpect(jsonPath("$.data.questions[1].questionText").value("두 번째 질문"));
    }

    @Test
    void addQuestion_returns201AndAppendsQuestionOrder() throws Exception {
        User user = persistUser("interview-add@example.com", "interview-add");
        Application application = persistApplication(user, "application-title", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "질문 세트", 2);
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
        User owner = persistUser("interview-owner-question@example.com", "owner");
        User otherUser = persistUser("interview-other-question@example.com", "other");
        Application application = persistApplication(owner, "application-title", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(owner, application, "질문 세트", 1);

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
        Application application = persistApplication(user, "application-title", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "질문 세트", 1);
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
        Application application = persistApplication(user, "application-title", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "질문 세트", 1);

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
        Application application = persistApplication(user, "application-title", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "질문 세트", 3);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        InterviewQuestion secondQuestion = persistQuestion(questionSet, 2, "두 번째 질문");
        persistQuestion(questionSet, 3, "세 번째 질문");

        mockMvc.perform(delete("/api/v1/interview/question-sets/{questionSetId}/questions/{questionId}",
                        questionSet.getId(), secondQuestion.getId())
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
        Application application = persistApplication(user, "application-title", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "질문 세트", 1);
        InterviewQuestion question = persistQuestion(questionSet, 1, "유일한 질문");

        mockMvc.perform(delete("/api/v1/interview/question-sets/{questionSetId}/questions/{questionId}",
                        questionSet.getId(), question.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("questionId"));
    }

    @Test
    void deleteQuestion_returns404WhenQuestionIsMissing() throws Exception {
        User user = persistUser("interview-delete-missing@example.com", "delete-missing");
        Application application = persistApplication(user, "application-title", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "질문 세트", 2);
        persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");

        mockMvc.perform(delete("/api/v1/interview/question-sets/{questionSetId}/questions/{questionId}",
                        questionSet.getId(), 999999L)
                        .with(authenticated(user.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    void deleteQuestion_returns409WhenQuestionSetAlreadyStarted() throws Exception {
        User user = persistUser("interview-delete-locked@example.com", "delete-locked");
        Application application = persistApplication(user, "application-title", ApplicationStatus.READY);
        InterviewQuestionSet questionSet = persistQuestionSet(user, application, "질문 세트", 2);
        InterviewQuestion question = persistQuestion(questionSet, 1, "첫 번째 질문");
        persistQuestion(questionSet, 2, "두 번째 질문");
        persistStartedSession(user, questionSet);

        mockMvc.perform(delete("/api/v1/interview/question-sets/{questionSetId}/questions/{questionId}",
                        questionSet.getId(), question.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_QUESTION_SET_NOT_EDITABLE.name()));
    }

    @Test
    void missingApiPath_returns404InsteadOf500() throws Exception {
        mockMvc.perform(get("/api/v1/interview/question-setz")
                        .with(authenticated(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
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

    private Application persistApplication(User user, String title, ApplicationStatus status) {
        Application application = Application.builder()
                .user(user)
                .applicationTitle(title)
                .companyName(title + "-company")
                .applicationType("신입")
                .jobRole("Backend Engineer")
                .status(status)
                .build();
        entityManager.persist(application);
        entityManager.flush();
        return application;
    }

    private ApplicationQuestion persistApplicationQuestion(
            Application application,
            int order,
            String questionText,
            String generatedAnswer
    ) {
        ApplicationQuestion question = ApplicationQuestion.builder()
                .application(application)
                .questionOrder(order)
                .questionText(questionText)
                .generatedAnswer(generatedAnswer)
                .editedAnswer(null)
                .build();
        entityManager.persist(question);
        entityManager.flush();
        return question;
    }

    private void persistDocument(User user, Application application, String extractedText) {
        Document document = Document.builder()
                .user(user)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.pdf")
                .storagePath("/documents/resume.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .extractStatus(DocumentExtractStatus.SUCCESS)
                .extractedText(extractedText)
                .uploadedAt(FIXED_NOW)
                .extractedAt(FIXED_NOW)
                .build();
        entityManager.persist(document);

        ApplicationSourceDocument binding = ApplicationSourceDocument.builder()
                .application(application)
                .document(document)
                .build();
        entityManager.persist(binding);
        entityManager.flush();
    }

    private InterviewQuestionSet persistQuestionSet(User user, Application application, String title, int questionCount) {
        InterviewQuestionSet questionSet = InterviewQuestionSet.builder()
                .user(user)
                .application(application)
                .title(title)
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
                .startedAt(FIXED_NOW)
                .endedAt(null)
                .build();
        entityManager.persist(session);
        entityManager.flush();
    }
}
