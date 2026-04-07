package com.back.backend.domain.auth.controller;

import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.global.security.jwt.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 부하 테스트 전용 토큰 발급 컨트롤러.
 * <p>
 * load-test 프로파일에서만 활성화된다.
 * /internal/load-test/** 경로는 CookieJwtAuthenticationFilter.shouldNotFilter()에 등록되어
 * 인증 없이 접근 가능하다.
 */
@RestController
@Profile("load-test")
@RequestMapping("/internal/load-test")
public class TestTokenController {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final JwtTokenService jwtTokenService;
    private final ApiKeyService apiKeyService;
    private final long testUserId;

    public TestTokenController(
            JwtTokenService jwtTokenService,
            ApiKeyService apiKeyService,
            @Value("${load-test.auth.test-user-id}") long testUserId
    ) {
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
        this.testUserId = testUserId;
    }

    /**
     * application-load-test.yml의 test-user-id로 24시간짜리 액세스 토큰과 API Key를 발급한다.
     * k6 setup()에서 한 번만 호출하고 이후 모든 VU가 공유한다.
     * Authorization 헤더 형식: Bearer {apiKey} {accessToken}
     */
    @GetMapping("/token")
    public ResponseEntity<ApiResponse<Map<String, String>>> token() {
        String accessToken = jwtTokenService.createToken(testUserId, TOKEN_TTL);
        String apiKey = apiKeyService.createApiKey(testUserId, TOKEN_TTL);
        return ResponseEntity.ok(ApiResponse.success(Map.of("token", accessToken, "apiKey", apiKey)));
    }
}
