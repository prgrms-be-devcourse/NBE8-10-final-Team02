package com.back.backend.domain.application.controller;

import com.back.backend.domain.application.dto.request.CreateApplicationRequest;
import com.back.backend.domain.application.dto.request.SaveApplicationQuestionsRequest;
import com.back.backend.domain.application.dto.request.SaveApplicationSourcesRequest;
import com.back.backend.domain.application.dto.request.UpdateApplicationRequest;
import com.back.backend.domain.application.repository.ApplicationQuestionRepository;
import com.back.backend.domain.application.repository.ApplicationRepository;
import com.back.backend.domain.application.repository.ApplicationSourceDocumentBindingRepository;
import com.back.backend.domain.application.repository.ApplicationSourceRepositoryBindingRepository;
import com.back.backend.domain.application.entity.Application;
import com.back.backend.domain.application.entity.ApplicationStatus;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.github.entity.GithubRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ApplicationApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationQuestionRepository applicationQuestionRepository;

    @Autowired
    private ApplicationSourceRepositoryBindingRepository applicationSourceRepositoryBindingRepository;

    @Autowired
    private ApplicationSourceDocumentBindingRepository applicationSourceDocumentBindingRepository;

    @Test
    void createApplication_returns201AndDraft() throws Exception {
        User user = fixtures.createUser("create@example.com", "creator");

        mockMvc.perform(post("/api/v1/applications")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new CreateApplicationRequest(
                                "네이버 백엔드 신입 지원",
                                "네이버",
                                "Backend Engineer",
                                "신입"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationTitle").value("네이버 백엔드 신입 지원"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.jobRole").value("Backend Engineer"));
    }

    @Test
    void getApplications_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void getApplication_returns404WhenOwnedByAnotherUser() throws Exception {
        User owner = fixtures.createUser("owner@example.com", "owner");
        User otherUser = fixtures.createUser("other@example.com", "other");
        Application application = fixtures.createApplication(owner, "owner-application", ApplicationStatus.DRAFT);

        mockMvc.perform(get("/api/v1/applications/{applicationId}", application.getId())
                        .with(authenticated(otherUser.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_NOT_FOUND.name()));
    }

    @Test
    void listApplications_returnsOnlyOwnedApplications() throws Exception {
        User user = fixtures.createUser("list-owner@example.com", "list-owner");
        User otherUser = fixtures.createUser("list-other@example.com", "list-other");

        fixtures.createApplication(user, "네이버", ApplicationStatus.DRAFT);
        fixtures.createApplication(user, "카카오", ApplicationStatus.DRAFT);
        fixtures.createApplication(otherUser, "타사", ApplicationStatus.DRAFT);

        mockMvc.perform(get("/api/v1/applications")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(content().string(not(containsString("타사"))));
    }

    @Test
    void updateApplication_returns409WhenReadyRequirementsNotMet() throws Exception {
        User user = fixtures.createUser("update@example.com", "updater");
        Application application = fixtures.createApplication(user, "draft-application", ApplicationStatus.DRAFT);

        mockMvc.perform(patch("/api/v1/applications/{applicationId}", application.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new UpdateApplicationRequest(
                                "updated-title",
                                "네이버",
                                "Backend Engineer",
                                "ready",
                                "신입"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_STATUS_CONFLICT.name()));
    }

    @Test
    void updateApplication_returns200WhenReadyRequirementsAreMet() throws Exception {
        User user = fixtures.createUser("ready@example.com", "ready-owner");
        Application application = fixtures.createApplication(user, "ready-application", ApplicationStatus.DRAFT);
        GithubRepository repository = persistGithubRepository(user, "team/ready-project");

        // ready 판정은 source 1개 이상과 문항별 usable answer를 함께 본다.
        // editedAnswer가 없어도 generatedAnswer가 있으면 최소 충족 조건으로 인정되는지 검증한다.
        fixtures.bindRepositoryToApplication(application, repository);
        fixtures.createApplicationQuestion(application, 1, "지원 동기", "포트폴리오 기반으로 작성된 자소서 초안");

        mockMvc.perform(patch("/api/v1/applications/{applicationId}", application.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new UpdateApplicationRequest(
                                null,
                                null,
                                "Backend Engineer",
                                "ready",
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ready"));
    }

    @Test
    void updateApplication_keepsExistingOptionalFieldsWhenPatchOmitsThem() throws Exception {
        User user = fixtures.createUser("patch@example.com", "patcher");
        Application application = fixtures.createApplication(user, "preserve-me", ApplicationStatus.DRAFT);

        mockMvc.perform(patch("/api/v1/applications/{applicationId}", application.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new UpdateApplicationRequest(
                                null,
                                null,
                                "Platform Engineer",
                                null,
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationTitle").value("preserve-me"))
                .andExpect(jsonPath("$.data.companyName").value("preserve-me-company"))
                .andExpect(jsonPath("$.data.applicationType").value("신입"))
                .andExpect(jsonPath("$.data.jobRole").value("Platform Engineer"));
    }

    @Test
    void deleteApplication_returns204() throws Exception {
        User user = fixtures.createUser("delete@example.com", "deleter");
        Application application = fixtures.createApplication(user, "delete-target", ApplicationStatus.DRAFT);

        mockMvc.perform(delete("/api/v1/applications/{applicationId}", application.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        assertThat(applicationRepository.findById(application.getId())).isEmpty();
    }

    @Test
    void saveSources_returns200WhenOwnedSourcesExist() throws Exception {
        User user = fixtures.createUser("sources@example.com", "sources-owner");
        Application application = fixtures.createApplication(user, "source-application", ApplicationStatus.DRAFT);
        GithubRepository repository = persistGithubRepository(user, "team/project-a");
        Document document = fixtures.createDocument(user, DocumentType.RESUME, "resume content");

        mockMvc.perform(put("/api/v1/applications/{applicationId}/sources", application.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SaveApplicationSourcesRequest(
                                List.of(repository.getId()),
                                List.of(document.getId())
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(application.getId()))
                .andExpect(jsonPath("$.data.sourceCount").value(2))
                .andExpect(jsonPath("$.data.repositoryIds[0]").value(repository.getId()))
                .andExpect(jsonPath("$.data.documentIds[0]").value(document.getId()));

        assertThat(applicationSourceRepositoryBindingRepository.countByApplicationId(application.getId())).isEqualTo(1L);
        assertThat(applicationSourceDocumentBindingRepository.countByApplicationId(application.getId())).isEqualTo(1L);
    }

    @Test
    void saveSources_returns404WhenRepositoryIsNotOwned() throws Exception {
        User user = fixtures.createUser("owner-sources@example.com", "owner-sources");
        User otherUser = fixtures.createUser("other-sources@example.com", "other-sources");
        Application application = fixtures.createApplication(user, "source-ownership", ApplicationStatus.DRAFT);
        GithubRepository otherUsersRepository = persistGithubRepository(otherUser, "other/project");

        mockMvc.perform(put("/api/v1/applications/{applicationId}/sources", application.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SaveApplicationSourcesRequest(
                                List.of(otherUsersRepository.getId()),
                                List.of()
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GITHUB_REPOSITORY_NOT_FOUND.name()));
    }

    @Test
    void saveQuestions_returns200AndQuestionsCanBeRead() throws Exception {
        User user = fixtures.createUser("questions@example.com", "questions-owner");
        Application application = fixtures.createApplication(user, "questions-application", ApplicationStatus.DRAFT);

        SaveApplicationQuestionsRequest request = new SaveApplicationQuestionsRequest(List.of(
                new SaveApplicationQuestionsRequest.QuestionItem(2, "두 번째 문항", "balanced", "long", "협업"),
                new SaveApplicationQuestionsRequest.QuestionItem(1, "첫 번째 문항", "formal", "medium", "성과")
        ));

        mockMvc.perform(post("/api/v1/applications/{applicationId}/questions", application.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].questionOrder").value(1))
                .andExpect(jsonPath("$.data[0].toneOption").value("formal"))
                .andExpect(jsonPath("$.data[1].questionOrder").value(2))
                .andExpect(jsonPath("$.data[1].lengthOption").value("long"));

        mockMvc.perform(get("/api/v1/applications/{applicationId}/questions", application.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].questionOrder").value(1))
                .andExpect(jsonPath("$.data[1].questionOrder").value(2));

        assertThat(applicationQuestionRepository.findAllByApplicationIdOrderByQuestionOrderAsc(application.getId()))
                .hasSize(2);
    }

    @Test
    void saveQuestions_returns400WhenQuestionCountExceedsLimit() throws Exception {
        User user = fixtures.createUser("limit@example.com", "limit-owner");
        Application application = fixtures.createApplication(user, "limit-application", ApplicationStatus.DRAFT);

        List<SaveApplicationQuestionsRequest.QuestionItem> questions = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(index -> new SaveApplicationQuestionsRequest.QuestionItem(
                        index,
                        "문항 " + index,
                        "formal",
                        "medium",
                        null
                ))
                .toList();

        mockMvc.perform(post("/api/v1/applications/{applicationId}/questions", application.getId())
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(new SaveApplicationQuestionsRequest(questions))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("questions"));
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }

    private GithubRepository persistGithubRepository(User user, String fullName) {
        var connection = fixtures.createConnection(user);
        return fixtures.createRepo(connection, fullName.substring(fullName.indexOf('/') + 1), true);
    }
}
