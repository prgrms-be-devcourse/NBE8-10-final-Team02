package com.back.backend.domain.github.controller;

import com.back.backend.domain.github.analysis.AnalysisPipelineService;
import com.back.backend.domain.github.dto.request.BatchAnalyzeRequest;
import com.back.backend.domain.github.dto.request.GithubConnectRequest;
import com.back.backend.domain.github.dto.request.RepositorySelectionRequest;
import com.back.backend.domain.github.analysis.SyncStatus;
import com.back.backend.domain.github.analysis.SyncStatusService;
import com.back.backend.domain.github.dto.response.GithubConnectionResponse;
import com.back.backend.domain.github.dto.response.GithubRepositoryResponse;
import com.back.backend.domain.github.dto.response.RepositorySelectionResponse;
import com.back.backend.domain.github.service.GithubConnectionService;
import com.back.backend.domain.github.service.GithubDiscoveryService;
import com.back.backend.domain.github.service.GithubRepositoryService;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GitHub API HTTP 레이어 통합 테스트.
 *
 * GithubConnectionService, GithubRepositoryService를 @MockitoBean으로 대체해
 * HTTP 레이어(인증, 응답 형식, 상태 코드)에만 집중합니다.
 */
class GithubApiTest extends ApiTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @MockitoBean
    private GithubConnectionService connectionService;

    @MockitoBean
    private GithubRepositoryService repositoryService;

    @MockitoBean
    private AnalysisPipelineService analysisPipelineService;

    @MockitoBean
    private GithubDiscoveryService discoveryService;

    @MockitoBean
    private SyncStatusService syncStatusService;

    // ─────────────────────────────────────────────────
    // GET /connections
    // ─────────────────────────────────────────────────

    @Test
    void getConnection_returns200WithConnectionDataWhenConnected() throws Exception {
        GithubConnectionResponse response = new GithubConnectionResponse(
                1L, 10L, 100001L, "github-user", "repo,user:email", "connected",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z")
        );
        given(connectionService.getConnectionOrNull(any())).willReturn(response);

        mockMvc.perform(get("/api/v1/github/connections")
                        .with(authenticated(10L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.githubLogin").value("github-user"))
                .andExpect(jsonPath("$.data.syncStatus").isNotEmpty());
    }

    @Test
    void getConnection_returns200WithNullDataWhenNotConnected() throws Exception {
        given(connectionService.getConnectionOrNull(any())).willReturn(null);

        mockMvc.perform(get("/api/v1/github/connections")
                        .with(authenticated(10L)))
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
        GithubConnectionResponse response = new GithubConnectionResponse(
                1L, 10L, 100001L, "github-user", "repo,user:email", "connected",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z")
        );
        given(connectionService.createOrUpdateConnection(any(), any())).willReturn(response);

        GithubConnectRequest request = new GithubConnectRequest("oauth", null, "test-token", "repo,user:email");

        mockMvc.perform(post("/api/v1/github/connections")
                        .with(authenticated(10L))
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
        willThrow(new ServiceException(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                HttpStatus.BAD_REQUEST,
                "oauth 모드에서는 accessToken이 필요합니다."
        )).given(connectionService).createOrUpdateConnection(any(), any());

        GithubConnectRequest request = new GithubConnectRequest("oauth", null, "   ", "repo");

        mockMvc.perform(post("/api/v1/github/connections")
                        .with(authenticated(10L))
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
        GithubRepositoryResponse repoA = new GithubRepositoryResponse(
                1L, 1001L, "github-user", "repo-a", "github-user/repo-a",
                "https://github.com/github-user/repo-a", "public", "main",
                true, false, null, false, null, "owner", "Java", null
        );
        GithubRepositoryResponse repoB = new GithubRepositoryResponse(
                2L, 1002L, "github-user", "repo-b", "github-user/repo-b",
                "https://github.com/github-user/repo-b", "public", "main",
                false, false, null, false, null, "owner", null, null
        );
        given(repositoryService.getRepositories(any(), eq(null), eq(1), eq(20)))
                .willReturn(new PageImpl<>(List.of(repoA, repoB), PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/api/v1/github/repositories")
                        .with(authenticated(10L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.pagination.totalElements").value(2));
    }

    @Test
    void getRepositories_returns200WithSelectedFilterApplied() throws Exception {
        GithubRepositoryResponse selected = new GithubRepositoryResponse(
                1L, 1001L, "github-user", "selected-repo", "github-user/selected-repo",
                "https://github.com/github-user/selected-repo", "public", "main",
                true, false, null, false, null, "owner", null, null
        );
        given(repositoryService.getRepositories(any(), eq(true), eq(1), eq(20)))
                .willReturn(new PageImpl<>(List.of(selected), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/github/repositories")
                        .param("selected", "true")
                        .with(authenticated(10L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].repoName").value("selected-repo"));
    }

    @Test
    void getRepositories_returns404WhenNoConnection() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.GITHUB_CONNECTION_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "GitHub 연결이 없습니다."
        )).given(repositoryService).getRepositories(any(), any(), anyInt(), anyInt());

        mockMvc.perform(get("/api/v1/github/repositories")
                        .with(authenticated(10L)))
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
        given(repositoryService.saveSelection(any(), any()))
                .willReturn(RepositorySelectionResponse.of(List.of(1L, 2L)));

        RepositorySelectionRequest request = new RepositorySelectionRequest(List.of(1L, 2L));

        mockMvc.perform(put("/api/v1/github/repositories/selection")
                        .with(authenticated(10L))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.selectedRepositoryIds").isArray())
                .andExpect(jsonPath("$.data.selectedRepositoryIds.length()").value(2));
    }

    @Test
    void updateSelection_returns403WhenRepositoryDoesNotBelongToUser() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.GITHUB_REPOSITORY_FORBIDDEN,
                HttpStatus.FORBIDDEN,
                "접근 권한이 없는 레포지토리입니다."
        )).given(repositoryService).saveSelection(any(), any());

        RepositorySelectionRequest request = new RepositorySelectionRequest(List.of(1L));

        mockMvc.perform(put("/api/v1/github/repositories/selection")
                        .with(authenticated(10L))
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

    // ─────────────────────────────────────────────────
    // POST /repositories/analyze-batch
    // ─────────────────────────────────────────────────

    @Test
    void analyzeBatch_returns202WhenRequestIsValid() throws Exception {
        BatchAnalyzeRequest request = new BatchAnalyzeRequest(List.of(1L, 2L));

        mockMvc.perform(post("/api/v1/github/repositories/analyze-batch")
                        .with(authenticated(10L))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void analyzeBatch_returns400WhenRepositoryIdsIsEmpty() throws Exception {
        // @NotEmpty Bean Validation이 서비스 호출 전에 400을 반환한다
        BatchAnalyzeRequest request = new BatchAnalyzeRequest(List.of());

        mockMvc.perform(post("/api/v1/github/repositories/analyze-batch")
                        .with(authenticated(10L))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeBatch_returns401WhenUnauthenticated() throws Exception {
        BatchAnalyzeRequest request = new BatchAnalyzeRequest(List.of(1L));

        mockMvc.perform(post("/api/v1/github/repositories/analyze-batch")
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analyzeBatch_returns409WhenAnalysisAlreadyInProgress() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                HttpStatus.CONFLICT,
                "이미 분석이 진행 중입니다: 1"
        )).given(analysisPipelineService).triggerBatchAnalysis(any(), any());

        BatchAnalyzeRequest request = new BatchAnalyzeRequest(List.of(1L));

        mockMvc.perform(post("/api/v1/github/repositories/analyze-batch")
                        .with(authenticated(10L))
                        .contentType("application/json")
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()));
    }

    @Test
    void analyzeRepository_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/github/repositories/1/analyze"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analyzeRepository_returns403WhenRepositoryDoesNotBelongToUser() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.GITHUB_REPOSITORY_FORBIDDEN,
                HttpStatus.FORBIDDEN,
                "접근 권한이 없는 저장소입니다."
        )).given(analysisPipelineService).triggerAnalysis(10L, 1L);

        mockMvc.perform(post("/api/v1/github/repositories/1/analyze")
                        .with(authenticated(10L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GITHUB_REPOSITORY_FORBIDDEN.name()));
    }

    @Test
    void analyzeRepository_returns409WhenAnalysisAlreadyInProgress() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                HttpStatus.CONFLICT,
                "이미 분석이 진행 중입니다."
        )).given(analysisPipelineService).triggerAnalysis(10L, 1L);

        mockMvc.perform(post("/api/v1/github/repositories/1/analyze")
                        .with(authenticated(10L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REQUEST_VALIDATION_FAILED.name()));
    }

    @Test
    void analyzeRepository_returnsAcceptedWithSyncStatusWhenQueued() throws Exception {
        given(syncStatusService.getStatus(10L, 1L)).willReturn(Optional.of(
                new SyncStatusService.SyncStatusData(
                        1L,
                        SyncStatus.PENDING,
                        null,
                        null,
                        Instant.parse("2026-01-01T00:05:00Z"),
                        null,
                        null,
                        null
                )));

        mockMvc.perform(post("/api/v1/github/repositories/1/analyze")
                        .with(authenticated(10L)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.repositoryId").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getDiscoveredContributions_returns403WhenScopeIsInsufficient() throws Exception {
        willThrow(new ServiceException(
                ErrorCode.GITHUB_SCOPE_INSUFFICIENT,
                HttpStatus.FORBIDDEN,
                "private repository 조회 권한이 부족합니다."
        )).given(discoveryService).findContributedRepos(10L, 0);

        mockMvc.perform(get("/api/v1/github/contributions/discovered")
                        .with(authenticated(10L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GITHUB_SCOPE_INSUFFICIENT.name()));
    }
}
