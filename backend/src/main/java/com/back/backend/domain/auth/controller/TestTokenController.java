package com.back.backend.domain.auth.controller;

import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.jwt.JwtTokenService;
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
 * /internal/** 경로는 SecurityConfig의 anyRequest().permitAll()에 해당하므로 인증 없이 접근 가능.
 */
@RestController
@Profile("load-test")
@RequestMapping("/internal/load-test")
public class TestTokenController {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    public TestTokenController(JwtTokenService jwtTokenService, UserRepository userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    /**
     * DB에서 첫 번째 유저의 ID로 24시간짜리 액세스 토큰을 발급한다.
     * k6 setup()에서 한 번만 호출하고 이후 모든 VU가 공유한다.
     */
    @GetMapping("/token")
    public ResponseEntity<ApiResponse<Map<String, String>>> token() {
        long userId = userRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("부하 테스트용 유저가 DB에 없습니다."))
                .getId();

        String accessToken = jwtTokenService.createToken(userId, TOKEN_TTL);
        return ResponseEntity.ok(ApiResponse.success(Map.of("token", accessToken)));
    }
}
