package com.back.backend.domain.auth.controller;

import com.back.backend.domain.github.service.GithubApiClient;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.support.ApiTestBase;
import com.back.backend.support.TestFixtures;
import jakarta.servlet.http.Cookie;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/auth/logout HTTP 계층 테스트.
 *
 * GitHub OAuth token 취소 로직이 로그아웃 흐름에 올바르게 통합됐는지 검증한다.
 * GithubApiClient는 @MockitoBean으로 대체해 실제 GitHub API 호출 없이 verify한다.
 * UserRepository, GithubConnectionRepository, ApiKeyService는 실제 빈(Testcontainers)을 사용한다.
 */
@Transactional
class AuthControllerLogoutTest extends ApiTestBase {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private ApiKeyService apiKeyService;

    @MockitoBean
    private GithubApiClient githubApiClient;

    // ─────────────────────────────────────────────────
    // 쿠키 초기화 + API key 무효화
    // ─────────────────────────────────────────────────

    @Test
    void logout_clearsCookiesAndInvalidatesApiKey() throws Exception {
        User user = fixtures.createUser("logout@example.com", "logout-user");
        String apiKey = apiKeyService.createApiKey(user.getId());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("apiKey", apiKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("로그아웃 성공"))
                .andExpect(cookie().maxAge("apiKey", 0))
                .andExpect(cookie().maxAge("accessToken", 0))
                .andExpect(cookie().maxAge("refreshToken", 0));

        // Redis에서도 삭제됐는지 확인
        assertThat(apiKeyService.validateAndGetUserId(apiKey)).isNull();
    }

    // ─────────────────────────────────────────────────
    // GitHub token 취소
    // ─────────────────────────────────────────────────

    @Test
    void logout_revokesGithubTokenWhenConnectionExists() throws Exception {
        User user = fixtures.createUser("github-logout@example.com", "github-user");
        fixtures.createConnection(user, 100001L, "github-user", "stored-github-token");
        String apiKey = apiKeyService.createApiKey(user.getId());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("apiKey", apiKey)))
                .andExpect(status().isOk());

        verify(githubApiClient).revokeAccessToken("stored-github-token");
    }

    @Test
    void logout_doesNotRevokeWhenNoGithubConnection() throws Exception {
        User user = fixtures.createUser("no-github@example.com", "no-github");
        String apiKey = apiKeyService.createApiKey(user.getId());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("apiKey", apiKey)))
                .andExpect(status().isOk());

        verify(githubApiClient, never()).revokeAccessToken(any());
    }

    // ─────────────────────────────────────────────────
    // 실패 내성 (fail-safe)
    // ─────────────────────────────────────────────────

    @Test
    void logout_succeedsEvenWhenGithubApiThrows() throws Exception {
        User user = fixtures.createUser("github-fail@example.com", "github-fail");
        fixtures.createConnection(user, 100002L, "github-fail", "bad-token");
        String apiKey = apiKeyService.createApiKey(user.getId());

        doThrow(new RuntimeException("GitHub API unavailable"))
                .when(githubApiClient).revokeAccessToken(any());

        // GitHub API 실패가 로그아웃을 막지 않아야 한다
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("apiKey", apiKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("로그아웃 성공"))
                .andExpect(cookie().maxAge("apiKey", 0));
    }

    @Test
    void logout_succeedsWithoutApiKeyCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("로그아웃 성공"));

        verify(githubApiClient, never()).revokeAccessToken(any());
    }
}
