package com.back.backend.domain.github.controller;

import com.back.backend.domain.github.dto.request.GithubConnectRequest;
import com.back.backend.domain.github.dto.request.RepositorySelectionRequest;
import com.back.backend.domain.github.entity.GithubConnection;
import com.back.backend.domain.github.entity.GithubRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GithubApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private TestFixtures fixtures;

    // ─────────────────────────────────────────────────
    // GET /connections
    // ─────────────────────────────────────────────────

    @Test
    void getConnection_returns200WithConnectionDataWhenConnected() throws Exception {
        User user = fixtures.createUser("conn-exists@example.com", "conn-exists");
        fixtures.createConnection(user);

        mockMvc.perform(get("/api/v1/github/connections")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.githubLogin").value("github-user"))
                .andExpect(jsonPath("$.data.syncStatus").isNotEmpty());
    }

    @Test
    void getConnection_returns200WithNullDataWhenNotConnected() throws Exception {
        User user = fixtures.createUser("conn-none@example.com", "conn-none");

        mockMvc.perform(get("/api/v1/github/connections")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void getConnection_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/github/connections"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────
    // POST /connections
    // ─────────────────────────────────────────────────

    @Test
    void createConnection_returns201AndSavesRepos() throws Exception {
        User user = fixtures.createUser("conn-create@example.com", "conn-create");

        wireMock.stubFor(WireMock.get(urlPathEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("github/user.json")));

        wireMock.stubFor(WireMock.get(urlPathEqualTo("/user/repos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("github/user-repos.json")));

        GithubConnectRequest request = new GithubConnectRequest("oauth", null, "test-token", "repo,user:email");

        mockMvc.perform(post("/api/v1/github/connections")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.githubLogin").value("github-user"))
                .andExpect(jsonPath("$.data.githubUserId").value(100001));
    }

    @Test
    void createConnection_returns401WhenUnauthenticated() throws Exception {
        GithubConnectRequest request = new GithubConnectRequest("oauth", null, "test-token", "repo");

        mockMvc.perform(post("/api/v1/github/connections")
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createConnection_returns400WhenAccessTokenIsBlank() throws Exception {
        User user = fixtures.createUser("conn-bad-token@example.com", "conn-bad-token");

        GithubConnectRequest request = new GithubConnectRequest("oauth", null, "   ", "repo");

        mockMvc.perform(post("/api/v1/github/connections")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()));
    }

    // ─────────────────────────────────────────────────
    // GET /repositories
    // ─────────────────────────────────────────────────

    @Test
    void getRepositories_returns200WithReposAndPagination() throws Exception {
        User user = fixtures.createUser("repo-list@example.com", "repo-list");
        GithubConnection connection = fixtures.createConnection(user);
        fixtures.createRepo(connection, "repo-a", true);
        fixtures.createRepo(connection, "repo-b", false);

        mockMvc.perform(get("/api/v1/github/repositories")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.pagination.totalElements").value(2));
    }

    @Test
    void getRepositories_returns200WithSelectedFilterApplied() throws Exception {
        User user = fixtures.createUser("repo-filter@example.com", "repo-filter");
        GithubConnection connection = fixtures.createConnection(user);
        fixtures.createRepo(connection, "selected-repo", true);
        fixtures.createRepo(connection, "unselected-repo", false);

        mockMvc.perform(get("/api/v1/github/repositories")
                        .param("selected", "true")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].repoName").value("selected-repo"));
    }

    @Test
    void getRepositories_returns404WhenNoConnection() throws Exception {
        User user = fixtures.createUser("repo-no-conn@example.com", "repo-no-conn");

        mockMvc.perform(get("/api/v1/github/repositories")
                        .with(authenticated(user.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GITHUB_CONNECTION_NOT_FOUND.name()));
    }

    @Test
    void getRepositories_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/github/repositories"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────
    // PUT /repositories/selection
    // ─────────────────────────────────────────────────

    @Test
    void updateSelection_returns200AndUpdatesSelection() throws Exception {
        User user = fixtures.createUser("sel-success@example.com", "sel-success");
        GithubConnection connection = fixtures.createConnection(user);
        GithubRepository repoA = fixtures.createRepo(connection, "repo-a", false);
        GithubRepository repoB = fixtures.createRepo(connection, "repo-b", false);

        RepositorySelectionRequest request = new RepositorySelectionRequest(List.of(repoA.getId(), repoB.getId()));

        mockMvc.perform(put("/api/v1/github/repositories/selection")
                        .with(authenticated(user.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.selectedRepositoryIds").isArray())
                .andExpect(jsonPath("$.data.selectedRepositoryIds.length()").value(2));
    }

    @Test
    void updateSelection_returns403WhenRepositoryDoesNotBelongToUser() throws Exception {
        User owner = fixtures.createUser("sel-owner@example.com", "sel-owner");
        User otherUser = fixtures.createUser("sel-other@example.com", "sel-other");

        GithubConnection ownerConnection = fixtures.createConnection(owner);
        GithubRepository ownerRepo = fixtures.createRepo(ownerConnection, "owner-repo", false);

        fixtures.createConnection(otherUser);

        RepositorySelectionRequest request = new RepositorySelectionRequest(List.of(ownerRepo.getId()));

        mockMvc.perform(put("/api/v1/github/repositories/selection")
                        .with(authenticated(otherUser.getId()))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN.name()));
    }

    @Test
    void updateSelection_returns401WhenUnauthenticated() throws Exception {
        RepositorySelectionRequest request = new RepositorySelectionRequest(List.of(1L));

        mockMvc.perform(put("/api/v1/github/repositories/selection")
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────
    // Private helper
    // ─────────────────────────────────────────────────

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }
}
