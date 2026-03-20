package com.back.backend.global.security;

import com.back.backend.global.security.jwt.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET_A =
        "insecure-dev-secret-please-change-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SECRET_B =
        "insecure-dev-secret-please-change-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private JwtTokenService serviceWith(String secret, Clock clock) {
        return new JwtTokenService(secret, 60, 120, clock);
    }

    @Test
    void createsAndParsesAccessToken() {
        Clock clock = Clock.systemUTC();
        JwtTokenService tokenService = serviceWith(SECRET_A, clock);

        String token = tokenService.createAccessToken(123L);
        Jws<Claims> parsed = tokenService.parseAccessToken(token);

        assertThat(parsed.getPayload().getSubject()).isEqualTo("123");
        assertThat(parsed.getPayload().getExpiration()).isNotNull();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        Clock clock = Clock.systemUTC();
        JwtTokenService tokenServiceA = serviceWith(SECRET_A, clock);
        JwtTokenService tokenServiceB = serviceWith(SECRET_B, clock);

        String token = tokenServiceA.createAccessToken(1L);

        assertThatThrownBy(() -> tokenServiceB.parseAccessToken(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        // 토큰 생성 시점: 과거 (accessTtl=60s → 200s 전에 발급된 토큰은 만료)
        Clock pastClock = Clock.fixed(Instant.now().minusSeconds(200), ZoneOffset.UTC);
        JwtTokenService issuer = serviceWith(SECRET_A, pastClock);
        String token = issuer.createAccessToken(1L);

        // 파싱은 현재 시각 기준으로 검증해야 만료 여부를 올바르게 판단
        JwtTokenService verifier = serviceWith(SECRET_A, Clock.systemUTC());
        assertThatThrownBy(() -> verifier.parseAccessToken(token))
            .isInstanceOf(JwtException.class);
    }
}
