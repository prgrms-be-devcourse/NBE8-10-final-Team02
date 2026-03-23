package com.back.backend.domain.user.controller;

import com.back.backend.domain.user.dto.response.UserProfileResponse;
import com.back.backend.domain.user.service.UserService;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.security.auth.JwtAuthenticationToken;
import com.back.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiTest extends ApiTestBase {

    @MockitoBean
    private UserService userService;

    @Test
    void getMe_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_REQUIRED.name()));
    }

    @Test
    void getMe_returns200WhenAuthenticated() throws Exception {
        given(userService.getMyProfile(1L)).willReturn(
                new UserProfileResponse(1L, "tester", "tester@example.com", null, "active")
        );

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(new JwtAuthenticationToken(1L, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.displayName").value("tester"))
                .andExpect(jsonPath("$.data.email").value("tester@example.com"))
                .andExpect(jsonPath("$.data.status").value("active"));
    }
}
