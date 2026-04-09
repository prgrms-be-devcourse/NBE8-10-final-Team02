package com.back.backend.global.security.auth;

import com.back.backend.global.security.CookieManager;
import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.global.security.handler.ApiAuthenticationEntryPoint;
import com.back.backend.global.security.jwt.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 쿠키와 헤더를 기반으로 JWT 및 API Key 인증을 처리하는 필터입니다.
 * <p>
 * 1. API Key 검증 (Redis)
 * 2. Access Token 검증 (JWT)
 * 3. Access Token 만료 시 Refresh Token을 통한 자동 재발급 (Refresh Token Rotation 포함)
 */
public class CookieJwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_API_KEY = "apiKey";
    public static final String COOKIE_ACCESS_TOKEN = "accessToken";
    public static final String COOKIE_REFRESH_TOKEN = "refreshToken";

    private final JwtTokenService jwtTokenService;
    private final ApiKeyService apiKeyService;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final CookieManager cookieManager;

    public CookieJwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            ApiKeyService apiKeyService,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            CookieManager cookieManager
    ) {
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.cookieManager = cookieManager;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // permitAll 경로는 인증 필터에서도 차단하지 않도록 스킵
        return path.startsWith("/actuator/")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/auth/oauth2/")
                || (path.startsWith("/api/v1/auth/oauth2/") && !path.equals("/api/v1/auth/oauth2/github/link-url"))
                || path.equals("/api/v1/auth/logout") // 만료된 토큰으로도 로그아웃 가능
                || path.equals("/api/v1/knowledge/sync") // localhost IP 제한은 SecurityConfig에서 처리
                || path.startsWith("/internal/load-test/") // load-test 프로파일 전용 내부 엔드포인트
                || path.startsWith("/contract-test/"); // TODO: 테스트용 경로 추후삭제필요
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 다른 인증 방식이 이미 설정한 경우 중복 처리하지 않음
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 우선순위: Authorization 헤더 -> 쿠키 폴백
        AuthorizationTokens authTokens = authorizationTokens(request);
        String apiKey = authTokens.apiKey() != null ? authTokens.apiKey() : cookieValue(request, COOKIE_API_KEY);
        String accessToken = authTokens.accessToken() != null ? authTokens.accessToken() : cookieValue(request, COOKIE_ACCESS_TOKEN);
        String refreshToken = cookieValue(request, COOKIE_REFRESH_TOKEN);

        // API Key가 없으면 인증 불가
        if (!StringUtils.hasText(apiKey)) {
            apiAuthenticationEntryPoint.commence(request, response, new AuthenticationRequiredException("로그인이 필요합니다."));
            return;
        }

        // API Key 검증 (Redis)
        Long apiKeyUserId = apiKeyService.validateAndGetUserId(apiKey);
        if (apiKeyUserId == null) {
            apiAuthenticationEntryPoint.commence(request, response, new AuthenticationInvalidTokenException("유효하지 않은 API Key입니다."));
            return;
        }

        // Access Token이 있으면 먼저 시도
        if (StringUtils.hasText(accessToken)) {
            try {
                long accessTokenUserId = authenticateWithAccessToken(accessToken);

                // API Key의 userId와 Access Token의 userId가 일치하는지 검증
                if (!apiKeyUserId.equals(accessTokenUserId)) {
                    apiAuthenticationEntryPoint.commence(request, response, new AuthenticationInvalidTokenException("API Key와 토큰이 일치하지 않습니다."));
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            } catch (AuthenticationExpiredTokenException ignored) {
                // Access Token 만료 시 Refresh Token 로직으로 넘어감
            } catch (AuthenticationInvalidTokenException invalid) {
                apiAuthenticationEntryPoint.commence(request, response, invalid);
                return;
            }
        }

        // Access Token이 없거나 만료된 경우 Refresh Token으로 재발급 시도
        if (StringUtils.hasText(refreshToken)) {
            try {
                // [RTR] Refresh Token Rotation: 새로운 Access Token과 함께 새로운 Refresh Token도 발급하여 세션 연장
                authenticateWithRefreshAndReissue(refreshToken, response);
                filterChain.doFilter(request, response);
                return;
            } catch (AuthenticationExpiredTokenException e) {
                apiAuthenticationEntryPoint.commence(request, response, e);
            } catch (AuthenticationInvalidTokenException e) {
                apiAuthenticationEntryPoint.commence(request, response, e);
            }
        } else {
            // Access Token이 문제가 있고 Refresh Token도 없는 경우
            apiAuthenticationEntryPoint.commence(request, response, new AuthenticationRequiredException("세션이 만료되었습니다. 다시 로그인해주세요."));
        }
    }

    private long authenticateWithAccessToken(String accessToken) {
        try {
            Jws<Claims> parsed = jwtTokenService.parseAccessToken(accessToken);
            long userId = Long.parseLong(parsed.getBody().getSubject());
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(userId, List.of()));
            return userId;
        } catch (ExpiredJwtException e) {
            throw new AuthenticationExpiredTokenException("세션이 만료되었습니다. 다시 로그인해주세요.", e);
        } catch (JwtException e) {
            throw new AuthenticationInvalidTokenException("유효하지 않은 토큰입니다.", e);
        }
    }

    /**
     * Refresh Token을 검증하고 새로운 Access Token 및 Refresh Token을 발급합니다 (Rotation).
     */
    private void authenticateWithRefreshAndReissue(String refreshToken, HttpServletResponse response) {
        try {
            Jws<Claims> parsed = jwtTokenService.parseRefreshToken(refreshToken);
            long userId = Long.parseLong(parsed.getBody().getSubject());

            // 새로운 토큰 세트 생성
            String newAccessToken = jwtTokenService.createAccessToken(userId);
            String newRefreshToken = jwtTokenService.createRefreshToken(userId);

            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(userId, List.of()));

            // 쿠키 갱신
            setAccessTokenCookie(response, newAccessToken);
            setRefreshTokenCookie(response, newRefreshToken);
        } catch (ExpiredJwtException e) {
            throw new AuthenticationExpiredTokenException("세션이 만료되었습니다. 다시 로그인해주세요.", e);
        } catch (JwtException e) {
            throw new AuthenticationInvalidTokenException("유효하지 않은 리프레시 토큰입니다.", e);
        }
    }

    private void setAccessTokenCookie(HttpServletResponse response, String newAccessToken) {
        cookieManager.add(response, COOKIE_ACCESS_TOKEN, newAccessToken, jwtTokenService.getAccessTtl());
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String newRefreshToken) {
        cookieManager.add(response, COOKIE_REFRESH_TOKEN, newRefreshToken, jwtTokenService.getRefreshTtl());
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private AuthorizationTokens authorizationTokens(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return AuthorizationTokens.none();
        }

        String afterBearer = authHeader.substring("Bearer ".length()).trim();
        String[] parts = afterBearer.split("\\s+");
        if (parts.length < 2) return AuthorizationTokens.none();

        return new AuthorizationTokens(parts[0], parts[1]);
    }

    private record AuthorizationTokens(String apiKey, String accessToken) {
        static AuthorizationTokens none() {
            return new AuthorizationTokens(null, null);
        }
    }
}
