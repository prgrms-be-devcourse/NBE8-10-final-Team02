package com.back.backend.domain.user.controller;

import com.back.backend.domain.auth.entity.AuthAccount;
import com.back.backend.domain.auth.entity.AuthProvider;
import com.back.backend.domain.auth.repository.AuthAccountRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import com.back.backend.support.TestFixtures;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UserApiTest extends ApiTestBase {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Test
    void getMe_returns200WithUserDataAndProviders() throws Exception {
        User user = fixtures.createUser("me-github@example.com", "me-github");
        authAccountRepository.save(AuthAccount.builder()
                .user(user)
                .provider(AuthProvider.GITHUB)
                .providerUserId("github-provider-123")
                .providerEmail("me-github@example.com")
                .primary(true)
                .connectedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.displayName").value("me-github"))
                .andExpect(jsonPath("$.data.email").value("me-github@example.com"))
                .andExpect(jsonPath("$.data.connectedProviders").isArray())
                .andExpect(jsonPath("$.data.connectedProviders[0]").value("github"));
    }

    @Test
    void getMe_returns200WithEmptyProvidersWhenNoSocialAccount() throws Exception {
        User user = fixtures.createUser("me-no-provider@example.com", "me-no-provider");

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticated(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.connectedProviders").isArray())
                .andExpect(jsonPath("$.data.connectedProviders.length()").value(0));
    }

    @Test
    void getMe_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor authenticated(long userId) {
        return authentication(new JwtAuthenticationToken(
                userId,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        ));
    }
}
