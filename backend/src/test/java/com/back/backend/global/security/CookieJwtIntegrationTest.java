package com.back.backend.global.security;

import com.back.backend.global.response.ApiResponse;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class CookieJwtIntegrationTest {

    @Test
    void cookieJwtAuthFlowWorksEndToEnd() throws Exception {
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
            ctx.getEnvironment().getSystemProperties().put("security.jwt.access-ttl-seconds", "5");
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

            // 2) invalid token -> AUTH_INVALID_TOKEN
            mockMvc.perform(get("/api/v1/test/protected")
                            .cookie(
                                    new jakarta.servlet.http.Cookie("apiKey", "k"),
                                    new jakarta.servlet.http.Cookie("accessToken", "not-a-jwt")
                            ))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));

            // 3) valid accessToken -> 200
            String okAccess = jwtTokenService.createAccessToken(101L);
            mockMvc.perform(get("/api/v1/test/protected")
                            .cookie(
                                    new jakarta.servlet.http.Cookie("apiKey", "k"),
                                    new jakarta.servlet.http.Cookie("accessToken", okAccess)
                            )
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(status().isOk());

            // 4) expired accessToken + refreshToken -> 200 and Set-Cookie(accessToken)
            String expiringAccess = jwtTokenService.createAccessToken(202L);
            String refresh = jwtTokenService.createRefreshToken(202L);
            Thread.sleep(5100);

            mockMvc.perform(get("/api/v1/test/protected")
                            .cookie(
                                    new jakarta.servlet.http.Cookie("apiKey", "k"),
                                    new jakarta.servlet.http.Cookie("accessToken", expiringAccess),
                                    new jakarta.servlet.http.Cookie("refreshToken", refresh)
                            ))
                    .andExpect(status().isOk())
                    .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("accessToken="))))
                    .andExpect(status().isOk());
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
    }
}

