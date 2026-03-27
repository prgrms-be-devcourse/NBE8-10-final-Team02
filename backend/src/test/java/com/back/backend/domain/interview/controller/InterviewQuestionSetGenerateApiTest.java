package com.back.backend.domain.interview.controller;

import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.interview.dto.request.CreateQuestionSetRequest;
import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestion;
import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import com.back.backend.domain.interview.entity.InterviewQuestionType;
import com.back.backend.domain.interview.repository.InterviewQuestionRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.back.backend.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InterviewQuestionSetGenerateApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEMPLATE_ID = "ai.interview.questions.generate.v1";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @MockitoBean
    private AiPipeline aiPipeline;

    @Test
    void createQuestionSet_returns201WithGeneratedQuestions() throws Exception {
        User user = fixtures.createUser("generate@example.com", "generate-tester");
        Application application = fixtures.createApplication(user, "카카오 백엔드");
        fixtures.createApplicationQuestion(application, 1, "지원 동기를 작성해주세요.", "저는 백엔드 개발자로서...");
        Document document = fixtures.createDocument(user, DocumentType.RESUME, "이력서 내용입니다.");
        fixtures.bindDocumentToApplication(application, document);

        given(aiPipeline.execute(eq(TEMPLATE_ID), anyString()))
            .willReturn(OBJECT_MAPPER.readTree("""
                {
                  "questions": [
                    {
                      "questionOrder": 1,
                      "questionType": "technical_cs",
                      "difficultyLevel": "medium",
                      "questionText": "Spring의 DI 컨테이너가 Bean을 관리하는 방식을 설명해주세요."
                    },
                    {
                      "questionOrder": 2,
                      "questionType": "project",
                      "difficultyLevel": "hard",
                      "questionText": "프로젝트에서 성능 병목을 어떻게 해결하셨나요?",
                      "sourceApplicationQuestionOrder": 1
                    }
                  ],
                  "qualityFlags": []
                }
                """));

        CreateQuestionSetRequest request = new CreateQuestionSetRequest(
            application.getId(), "면접 질문 세트", 2, "medium",
            List.of("technical_cs", "project")
        );

        mockMvc.perform(post("/api/v1/interview/question-sets")
                .with(authenticated(user.getId()))
                .contentType("application/json")
                .content(OBJECT_MAPPER.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("면접 질문 세트"))
            .andExpect(jsonPath("$.data.questionCount").value(2))
            .andExpect(jsonPath("$.data.difficultyLevel").value("medium"))
            .andExpect(jsonPath("$.data.applicationId").value(application.getId()));

        entityManager.flush();
        entityManager.clear();

        List<InterviewQuestionSet> savedSets = entityManager
            .createQuery("SELECT qs FROM InterviewQuestionSet qs WHERE qs.user.id = :userId", InterviewQuestionSet.class)
            .setParameter("userId", user.getId())
            .getResultList();
        assertThat(savedSets).hasSize(1);

        List<InterviewQuestion> savedQuestions = interviewQuestionRepository
            .findAllByQuestionSetIdOrderByQuestionOrderAsc(savedSets.get(0).getId());
        assertThat(savedQuestions).hasSize(2);
        assertThat(savedQuestions.get(0).getQuestionType()).isEqualTo(InterviewQuestionType.TECHNICAL_CS);
        assertThat(savedQuestions.get(0).getQuestionText()).isEqualTo("Spring의 DI 컨테이너가 Bean을 관리하는 방식을 설명해주세요.");
        assertThat(savedQuestions.get(1).getQuestionType()).isEqualTo(InterviewQuestionType.PROJECT);
        assertThat(savedQuestions.get(1).getDifficultyLevel()).isEqualTo(DifficultyLevel.HARD);
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
        User owner = fixtures.createUser("owner@example.com", "owner");
        User other = fixtures.createUser("other@example.com", "other");
        Application application = fixtures.createApplication(owner, "카카오 백엔드");

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

    @Test
    void getQuestionSets_returns200WithList() throws Exception {
        User user = fixtures.createUser("list@example.com", "list-tester");
        Application application = fixtures.createApplication(user, "카카오 백엔드");
        fixtures.createQuestionSet(user, application, "세트1", 3);
        fixtures.createQuestionSet(user, application, "세트2", 5);

        mockMvc.perform(get("/api/v1/interview/question-sets")
                .with(authenticated(user.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getQuestionSet_returns200WithDetail() throws Exception {
        User user = fixtures.createUser("detail@example.com", "detail-tester");
        Application application = fixtures.createApplication(user, "카카오 백엔드");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(user, application, "상세 세트", 2);
        createQuestion(questionSet, 1, "첫 번째 질문", InterviewQuestionType.TECHNICAL_CS);
        createQuestion(questionSet, 2, "두 번째 질문", InterviewQuestionType.PROJECT);

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
        User owner = fixtures.createUser("qs-owner@example.com", "qs-owner");
        User other = fixtures.createUser("qs-other@example.com", "qs-other");
        Application application = fixtures.createApplication(owner, "카카오 백엔드");
        InterviewQuestionSet questionSet = fixtures.createQuestionSet(owner, application, "세트", 1);

        mockMvc.perform(get("/api/v1/interview/question-sets/{questionSetId}", questionSet.getId())
                .with(authenticated(other.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERVIEW_QUESTION_SET_NOT_FOUND.name()));
    }

    // --- 헬퍼 ---

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
            userId,
            AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }

    /** TestFixtures.createInterviewQuestion은 타입을 PROJECT로 고정하므로 타입 지정이 필요한 경우에만 사용 */
    private InterviewQuestion createQuestion(InterviewQuestionSet questionSet, int order,
                                              String questionText, InterviewQuestionType type) {
        return interviewQuestionRepository.save(InterviewQuestion.builder()
            .questionSet(questionSet)
            .questionOrder(order)
            .questionType(type)
            .difficultyLevel(DifficultyLevel.MEDIUM)
            .questionText(questionText)
            .build());
    }
}
