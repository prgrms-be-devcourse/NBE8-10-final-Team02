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
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
import com.back.backend.domain.github.entity.GithubSyncStatus;
import com.back.backend.domain.github.entity.RepositoryVisibility;
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

    private static final Instant NOW = Instant.parse("2026-03-23T09:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationQuestionRepository applicationQuestionRepository;

    @Autowired
    private ApplicationSourceRepositoryBindingRepository applicationSourceRepositoryBindingRepository;

    @Autowired
    private ApplicationSourceDocumentBindingRepository applicationSourceDocumentBindingRepository;

    private long nextGithubUserId = 1000L;
    private long nextGithubRepoId = 2000L;

    @Test
    void createApplication_returns201AndDraft() throws Exception {
        User user = persistUser("create@example.com", "creator");

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
        User owner = persistUser("owner@example.com", "owner");
        User otherUser = persistUser("other@example.com", "other");
        Application application = persistApplication(owner, "owner-application", ApplicationStatus.DRAFT);

        mockMvc.perform(get("/api/v1/applications/{applicationId}", application.getId())
                        .with(authenticated(otherUser.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.APPLICATION_NOT_FOUND.name()));
    }

    @Test
    void listApplications_returnsOnlyOwnedApplications() throws Exception {
        User user = persistUser("list-owner@example.com", "list-owner");
        User otherUser = persistUser("list-other@example.com", "list-other");

        persistApplication(user, "네이버", ApplicationStatus.DRAFT);
        persistApplication(user, "카카오", ApplicationStatus.DRAFT);
        persistApplication(otherUser, "타사", ApplicationStatus.DRAFT);

        mockMvc.perform(get("/api/v1/applications")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(content().string(not(containsString("타사"))));
    }

    @Test
    void updateApplication_returns409WhenReadyRequirementsNotMet() throws Exception {
        User user = persistUser("update@example.com", "updater");
        Application application = persistApplication(user, "draft-application", ApplicationStatus.DRAFT);

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
    void updateApplication_keepsExistingOptionalFieldsWhenPatchOmitsThem() throws Exception {
        User user = persistUser("patch@example.com", "patcher");
        Application application = persistApplication(user, "preserve-me", ApplicationStatus.DRAFT);

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
        User user = persistUser("delete@example.com", "deleter");
        Application application = persistApplication(user, "delete-target", ApplicationStatus.DRAFT);

        mockMvc.perform(delete("/api/v1/applications/{applicationId}", application.getId())
                        .with(authenticated(user.getId())))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        assertThat(applicationRepository.findById(application.getId())).isEmpty();
    }

    @Test
    void saveSources_returns200WhenOwnedSourcesExist() throws Exception {
        User user = persistUser("sources@example.com", "sources-owner");
        Application application = persistApplication(user, "source-application", ApplicationStatus.DRAFT);
        GithubRepository repository = persistGithubRepository(user, "team/project-a");
        Document document = persistDocument(user, "resume.md");

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
        User user = persistUser("owner-sources@example.com", "owner-sources");
        User otherUser = persistUser("other-sources@example.com", "other-sources");
        Application application = persistApplication(user, "source-ownership", ApplicationStatus.DRAFT);
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
        User user = persistUser("questions@example.com", "questions-owner");
        Application application = persistApplication(user, "questions-application", ApplicationStatus.DRAFT);

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
        User user = persistUser("limit@example.com", "limit-owner");
        Application application = persistApplication(user, "limit-application", ApplicationStatus.DRAFT);

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

    private GithubRepository persistGithubRepository(User user, String fullName) {
        GithubConnection connection = GithubConnection.builder()
                .user(user)
                .githubUserId(nextGithubUserId++)
                .githubLogin("github-" + user.getId())
                .accessScope("repo")
                .syncStatus(GithubSyncStatus.SUCCESS)
                .connectedAt(NOW)
                .lastSyncedAt(NOW)
                .build();
        entityManager.persist(connection);

        GithubRepository repository = GithubRepository.builder()
                .githubConnection(connection)
                .githubRepoId(nextGithubRepoId++)
                .ownerLogin("owner-" + user.getId())
                .repoName(fullName.substring(fullName.indexOf('/') + 1))
                .fullName(fullName)
                .htmlUrl("https://github.com/" + fullName)
                .visibility(RepositoryVisibility.PUBLIC)
                .defaultBranch("main")
                .selected(true)
                .syncedAt(NOW)
                .build();
        entityManager.persist(repository);
        entityManager.flush();
        return repository;
    }

    private Document persistDocument(User user, String fileName) {
        Document document = Document.builder()
                .user(user)
                .documentType(DocumentType.RESUME)
                .originalFileName(fileName)
                .storagePath("/documents/" + fileName)
                .mimeType("text/markdown")
                .fileSizeBytes(128L)
                .extractStatus(DocumentExtractStatus.SUCCESS)
                .uploadedAt(NOW)
                .extractedAt(NOW)
                .extractedText("resume content")
                .build();
        entityManager.persist(document);
        entityManager.flush();
        return document;
    }
}
