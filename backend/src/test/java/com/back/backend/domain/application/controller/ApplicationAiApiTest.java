package com.back.backend.domain.application.controller;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationLengthOption;
import com.back.backend.domain.application.entity.ApplicationQuestion;
import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.application.entity.ApplicationToneOption;
import com.back.backend.domain.application.dto.request.GenerateAnswersRequest;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ApplicationAiApiTest extends ApiTestBase {

    private static final Instant NOW = Instant.parse("2026-03-25T09:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEMPLATE_ID = "ai.self_intro.generate.v1";

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private AiPipeline aiPipeline;

    @Test
    void generateAnswers_returns200WithGeneratedAnswers() throws Exception {
        User user = persistUser("ai@example.com", "ai-tester");
        Application application = persistApplication(user, "카카오 백엔드", ApplicationStatus.DRAFT);
        persistQuestion(application, 1, "지원 동기를 작성해주세요", null,
            ApplicationToneOption.FORMAL, ApplicationLengthOption.MEDIUM, "프로젝트 경험");
        persistDocument(user, application, "이력서 내용입니다.");

        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
            .willReturn(OBJECT_MAPPER.readTree("""
                {
                  "answers": [
                    { "questionOrder": 1, "answerText": "카카오의 기술 문화에 깊은 관심을 가지고 있습니다." }
                  ]
                }
                """));

        mockMvc.perform(post("/api/v1/applications/{applicationId}/questions/generate-answers",
                application.getId())
                .with(authenticated(user.getId()))
                .contentType("application/json")
                .content(OBJECT_MAPPER.writeValueAsString(
                    new GenerateAnswersRequest(true, false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.applicationId").value(application.getId()))
            .andExpect(jsonPath("$.data.generatedCount").value(1))
            .andExpect(jsonPath("$.data.regenerate").value(false))
            .andExpect(jsonPath("$.data.answers.length()").value(1))
            .andExpect(jsonPath("$.data.answers[0].questionText").value("지원 동기를 작성해주세요"))
            .andExpect(jsonPath("$.data.answers[0].generatedAnswer")
                .value("카카오의 기술 문화에 깊은 관심을 가지고 있습니다."))
            .andExpect(jsonPath("$.data.answers[0].toneOption").value("formal"))
            .andExpect(jsonPath("$.data.answers[0].lengthOption").value("medium"));

        entityManager.flush();
        entityManager.clear();

        Application refreshed = entityManager.find(Application.class, application.getId());
        assertThat(refreshed.getStatus()).isEqualTo(ApplicationStatus.READY);
    }

    @Test
    void generateAnswers_skipsAlreadyAnsweredWhenRegenerateFalse() throws Exception {
        User user = persistUser("skip@example.com", "skip-tester");
        Application application = persistApplication(user, "네이버 백엔드", ApplicationStatus.DRAFT);
        persistQuestion(application, 1, "지원 동기", "기존 답변", null, null, null);
        persistQuestion(application, 2, "프로젝트 경험", null, null, null, null);

        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
            .willReturn(OBJECT_MAPPER.readTree("""
                {
                  "answers": [
                    { "questionOrder": 2, "answerText": "새로 생성된 답변" }
                  ]
                }
                """));

        mockMvc.perform(post("/api/v1/applications/{applicationId}/questions/generate-answers",
                application.getId())
                .with(authenticated(user.getId()))
                .contentType("application/json")
                .content(OBJECT_MAPPER.writeValueAsString(
                    new GenerateAnswersRequest(true, false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.generatedCount").value(1))
            .andExpect(jsonPath("$.data.answers.length()").value(2))
            .andExpect(jsonPath("$.data.answers[0].generatedAnswer").value("기존 답변"))
            .andExpect(jsonPath("$.data.answers[1].generatedAnswer").value("새로 생성된 답변"));
    }

    @Test
    void generateAnswers_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/applications/1/questions/generate-answers")
                .contentType("application/json")
                .content(OBJECT_MAPPER.writeValueAsString(
                    new GenerateAnswersRequest(true, false))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void generateAnswers_returns404WhenApplicationNotOwned() throws Exception {
        User owner = persistUser("owner@example.com", "owner");
        User other = persistUser("other@example.com", "other");
        Application application = persistApplication(owner, "남의 지원서", ApplicationStatus.DRAFT);

        mockMvc.perform(post("/api/v1/applications/{applicationId}/questions/generate-answers",
                application.getId())
                .with(authenticated(other.getId()))
                .contentType("application/json")
                .content(OBJECT_MAPPER.writeValueAsString(
                    new GenerateAnswersRequest(true, false))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_NOT_FOUND.name()));
    }

    @Test
    void generateAnswers_returns422WhenNoQuestions() throws Exception {
        User user = persistUser("empty@example.com", "empty-tester");
        Application application = persistApplication(user, "문항 없는 지원서", ApplicationStatus.DRAFT);

        mockMvc.perform(post("/api/v1/applications/{applicationId}/questions/generate-answers",
                application.getId())
                .with(authenticated(user.getId()))
                .contentType("application/json")
                .content(OBJECT_MAPPER.writeValueAsString(
                    new GenerateAnswersRequest(true, false))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_QUESTION_REQUIRED.name()));
    }

    // --- 헬퍼 ---

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

    private void persistQuestion(Application application, int order, String text,
                                 String generatedAnswer, ApplicationToneOption tone,
                                 ApplicationLengthOption length, String emphasisPoint) {
        ApplicationQuestion question = ApplicationQuestion.builder()
            .application(application)
            .questionOrder(order)
            .questionText(text)
            .generatedAnswer(generatedAnswer)
            .toneOption(tone)
            .lengthOption(length)
            .emphasisPoint(emphasisPoint)
            .build();
        entityManager.persist(question);
        entityManager.flush();
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
            .uploadedAt(NOW)
            .extractedAt(NOW)
            .build();
        entityManager.persist(document);

        ApplicationSourceDocument binding = ApplicationSourceDocument.builder()
            .application(application)
            .document(document)
            .build();
        entityManager.persist(binding);
        entityManager.flush();
    }
}
