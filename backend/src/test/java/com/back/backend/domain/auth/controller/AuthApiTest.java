package com.back.backend.domain.auth.controller;

import com.back.backend.domain.auth.dto.response.AuthorizationStartResponse;
import com.back.backend.domain.auth.dto.response.LogoutResponse;
import com.back.backend.domain.auth.service.AuthService;
import com.back.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiTestBase {

    @MockitoBean
    private AuthService authService;

    @Test
    void getAuthorizeUrl_returnsAuthorizationData() throws Exception {
        given(authService.createAuthorizationStart(eq("github"), any(), any()))
                .willReturn(new AuthorizationStartResponse(
                        "github",
                        "https://api.example.com/oauth2/authorization/github?redirectUrl=https%3A%2F%2Ffrontend.example.com",
                        ""
                ));

        mockMvc.perform(get("/api/v1/auth/oauth2/github/authorize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.provider").value("github"))
                .andExpect(jsonPath("$.data.authorizationUrl").value("https://api.example.com/oauth2/authorization/github?redirectUrl=https%3A%2F%2Ffrontend.example.com"))
                .andExpect(jsonPath("$.data.state").value(""));
    }

    @Test
    void logout_returnsLoggedOutResponse() throws Exception {
        given(authService.logout(nullable(String.class))).willReturn(new LogoutResponse(true));

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loggedOut").value(true));
    }
}
