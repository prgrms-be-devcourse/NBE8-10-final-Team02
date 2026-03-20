package com.back.backend.global.security;

import com.back.backend.global.security.jwt.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void createsAndParsesAccessToken() {
        JwtTokenService tokenService = new JwtTokenService(
                "insecure-dev-secret-please-change-123456789012345678901234567890",
                60,
                120
        );
        tokenService.init();

        String token = tokenService.createAccessToken(123L);
        Jws<Claims> parsed = tokenService.parseAccessToken(token);

        assertThat(parsed.getBody().getSubject()).isEqualTo("123");
        assertThat(parsed.getBody().getExpiration()).isNotNull();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtTokenService tokenServiceA = new JwtTokenService(
                "insecure-dev-secret-please-change-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                60,
                120
        );
        tokenServiceA.init();

        JwtTokenService tokenServiceB = new JwtTokenService(
                "insecure-dev-secret-please-change-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                60,
                120
        );
        tokenServiceB.init();

        String token = tokenServiceA.createAccessToken(1L);

        assertThatThrownBy(() -> tokenServiceB.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }
}

