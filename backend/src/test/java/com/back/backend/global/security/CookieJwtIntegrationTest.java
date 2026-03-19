package com.back.backend.global.security;

import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.apikey.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockServletContext;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class CookieJwtIntegrationTest {

    @Test
    void cookieJwtAuthFlowWorksEndToEnd() throws Exception {
        // TODO: @SpringBootTest가 무거워서 다이어트시킨 버전. Testcontinaer와 통합테스트로 수정필요.
        try (AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext()) {
            ctx.setServletContext(new MockServletContext());
            ctx.register(
                    com.back.backend.global.security.config.SecurityConfig.class,
                    com.back.backend.global.request.RequestIdFilter.class,
                    com.back.backend.global.security.handler.ApiAuthenticationEntryPoint.class,
                    com.back.backend.global.response.ApiErrorResponseWriter.class,
                    com.back.backend.global.security.auth.CookieJwtAuthenticationFilter.class,
                    com.back.backend.global.security.jwt.JwtTokenService.class,
                    ProtectedTestController.class,
                    TestOnlyBeans.class
            );
            ctx.getEnvironment().getSystemProperties().put("security.jwt.secret",
                    "insecure-dev-secret-please-change-123456789012345678901234567890");
            ctx.getEnvironment().getSystemProperties().put("security.jwt.access-ttl-seconds", "1");
            ctx.getEnvironment().getSystemProperties().put("security.jwt.refresh-ttl-seconds", "60");
            ctx.getEnvironment().getSystemProperties().put("security.cookie.secure", "false");
            ctx.refresh();

            com.back.backend.global.security.jwt.JwtTokenService jwtTokenService =
                    ctx.getBean(com.back.backend.global.security.jwt.JwtTokenService.class);

            MockMvc mockMvc = MockMvcBuilders
                    .webAppContextSetup(ctx)
                    .apply(springSecurity())
                    .build();

            // 1) no cookie -> AUTH_REQUIRED
            mockMvc.perform(get("/api/v1/test/protected"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));

            // 2) invalid apiKey -> AUTH_INVALID_TOKEN
            mockMvc.perform(get("/api/v1/test/protected")
                            .cookie(
                                    new jakarta.servlet.http.Cookie("apiKey", "invalid-key"),
                                    new jakarta.servlet.http.Cookie("accessToken", "some-token")
                            ))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));

            // 3) valid apiKey + valid accessToken -> 200
            String okAccess = jwtTokenService.createAccessToken(101L);
            mockMvc.perform(get("/api/v1/test/protected")
                            .cookie(
                                    new jakarta.servlet.http.Cookie("apiKey", "valid-api-key-101"),
                                    new jakarta.servlet.http.Cookie("accessToken", okAccess)
                            )
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            // 4) apiKey userId != accessToken userId -> AUTH_INVALID_TOKEN
            String access202 = jwtTokenService.createAccessToken(202L);
            mockMvc.perform(get("/api/v1/test/protected")
                            .cookie(
                                    new jakarta.servlet.http.Cookie("apiKey", "valid-api-key-101"), // userId 101
                                    new jakarta.servlet.http.Cookie("accessToken", access202) // userId 202
                            ))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));

            // 5) expired accessToken + refreshToken -> 200 and Set-Cookie(accessToken)
            String expiringAccess = jwtTokenService.createAccessToken(202L);
            String refresh = jwtTokenService.createRefreshToken(202L);
            Thread.sleep(1200);

            mockMvc.perform(get("/api/v1/test/protected")
                            .cookie(
                                    new jakarta.servlet.http.Cookie("apiKey", "valid-api-key-202"),
                                    new jakarta.servlet.http.Cookie("accessToken", expiringAccess),
                                    new jakarta.servlet.http.Cookie("refreshToken", refresh)
                            ))
                    .andExpect(status().isOk())
                    .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("accessToken="))));
        }
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class ProtectedTestController {
        @GetMapping("/protected")
        String protectedEndpoint() {
            return "ok";
        }
    }

    @Configuration
    static class TestOnlyBeans {
        @Bean
        tools.jackson.databind.ObjectMapper toolsObjectMapper() {
            return new tools.jackson.databind.ObjectMapper();
        }

        @Bean
        ApiKeyService apiKeyService() {
            // Mock ApiKeyService: "valid-api-key-101" -> userId 101, "valid-api-key-202" -> userId 202
            ApiKeyService mockService = mock(ApiKeyService.class);
            // anyString()을 먼저 설정 (기본값)
            when(mockService.validateAndGetUserId(anyString())).thenReturn(null);
            // 구체적인 값들을 나중에 설정 (우선순위 높음)
            when(mockService.validateAndGetUserId("valid-api-key-101")).thenReturn(101L);
            when(mockService.validateAndGetUserId("valid-api-key-202")).thenReturn(202L);
            return mockService;
        }
    }
}

