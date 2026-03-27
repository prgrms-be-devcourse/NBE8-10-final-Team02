package com.back.backend.domain.application.controller;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationLengthOption;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.application.entity.ApplicationToneOption;
import com.back.backend.domain.application.dto.request.GenerateAnswersRequest;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.back.backend.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ApplicationAiApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEMPLATE_ID = "ai.self_intro.generate.v1";

    @Autowired
    private TestFixtures fixtures;

    @MockitoBean
    private AiPipeline aiPipeline;

    @Test
    void generateAnswers_returns200WithGeneratedAnswers() throws Exception {
        User user = fixtures.createUser("ai@example.com", "ai-tester");
        Application application = fixtures.createApplication(user, "카카오 백엔드", ApplicationStatus.DRAFT);
        fixtures.createApplicationQuestion(application, 1, "지원 동기를 작성해주세요", null,
            ApplicationToneOption.FORMAL, ApplicationLengthOption.MEDIUM, "프로젝트 경험");
        fixtures.bindDocumentToApplication(application, fixtures.createResumeDocument(user, "이력서 내용입니다."));

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
    }

    @Test
    void generateAnswers_skipsAlreadyAnsweredWhenRegenerateFalse() throws Exception {
        User user = fixtures.createUser("skip@example.com", "skip-tester");
        Application application = fixtures.createApplication(user, "네이버 백엔드", ApplicationStatus.DRAFT);
        fixtures.createApplicationQuestion(application, 1, "지원 동기", "기존 답변", null, null, null);
        fixtures.createApplicationQuestion(application, 2, "프로젝트 경험", null, null, null, null);

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
        User owner = fixtures.createUser("owner@example.com", "owner");
        User other = fixtures.createUser("other@example.com", "other");
        Application application = fixtures.createApplication(owner, "남의 지원서", ApplicationStatus.DRAFT);

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
        User user = fixtures.createUser("empty@example.com", "empty-tester");
        Application application = fixtures.createApplication(user, "문항 없는 지원서", ApplicationStatus.DRAFT);

        mockMvc.perform(post("/api/v1/applications/{applicationId}/questions/generate-answers",
                application.getId())
                .with(authenticated(user.getId()))
                .contentType("application/json")
                .content(OBJECT_MAPPER.writeValueAsString(
                    new GenerateAnswersRequest(true, false))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_QUESTION_REQUIRED.name()));
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
            userId,
            AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }
}
