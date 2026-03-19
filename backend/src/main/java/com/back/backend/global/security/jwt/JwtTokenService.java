package com.back.backend.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
/**
 * MVP token service.
 * <p>
 * 프로젝트 문서에는 JWT 클레임 스펙이 구체적으로 정의되어 있지 않아서,
 * 최소한의 표준 필드(`sub`, `exp`)만 사용합니다.
 */
@Service
public class JwtTokenService {

    private final String jwtSecret;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    private SecretKey signingKey;

    public JwtTokenService(
            @Value("${security.jwt.secret:insecure-dev-secret-please-change-123456789012345678901234567890}") String jwtSecret,
            @Value("${security.jwt.access-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${security.jwt.refresh-ttl-seconds:86400}") long refreshTtlSeconds
    ) {
        this.jwtSecret = jwtSecret;
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
        this.refreshTtl = Duration.ofSeconds(refreshTtlSeconds);
    }

    @PostConstruct
    void init() {
        // secret이 base64라고 가정하지 않고, 그대로 사용하도록 처리합니다.
        // (Keys.hmacShaKeyFor는 길이 조건을 만족하지 않으면 예외를 던질 수 있음)
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public String createAccessToken(long userId) {
        return createToken(userId, accessTtl);
    }

    public String createRefreshToken(long userId) {
        return createToken(userId, refreshTtl);
    }

    private String createToken(long userId, Duration ttl) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            // signingKey가 SecretKey 타입이라면 signWith(signingKey)만 적어도 됩니다.
            // 명시적으로 알고리즘을 지정하려면 Jwts.SIG.HS256 을 사용합니다.
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    public Jws<Claims> parseAccessToken(String token) throws JwtException {
        return parse(token);
    }

    public Jws<Claims> parseRefreshToken(String token) throws JwtException {
        return parse(token);
    }

    private Jws<Claims> parse(String token) throws JwtException {
        // JWT 서명/exp 검증까지 포함
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }
}

