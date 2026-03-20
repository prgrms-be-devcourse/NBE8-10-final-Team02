package com.back.backend.global.security;

import com.back.backend.global.response.ApiErrorResponseWriter;
import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.global.security.auth.CookieJwtAuthenticationFilter;
import com.back.backend.global.security.config.SecurityConfig;
import com.back.backend.global.security.handler.ApiAuthenticationEntryPoint;
import com.back.backend.global.security.jwt.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CookieJwtSecurityTest.ProtectedTestController.class)
@Import({
    SecurityConfig.class,
    CookieJwtAuthenticationFilter.class,
    JwtTokenService.class,
    ApiAuthenticationEntryPoint.class,
    ApiErrorResponseWriter.class
})
@TestPropertySource(properties = "security.jwt.access-ttl-seconds=1")
@ActiveProfiles("test")
class CookieJwtSecurityTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUpApiKeyService() {
        when(apiKeyService.validateAndGetUserId(anyString())).thenReturn(null);
        when(apiKeyService.validateAndGetUserId("valid-api-key-101")).thenReturn(101L);
        when(apiKeyService.validateAndGetUserId("valid-api-key-202")).thenReturn(202L);
    }

    @Test
    void cookieJwtAuthFlowWorksEndToEnd() throws Exception {

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
                .andDo(print())
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

    @RestController
    @RequestMapping("/api/v1/test")
    static class ProtectedTestController {
        @GetMapping("/protected")
        String protectedEndpoint() {
            return "ok";
        }
    }
}
