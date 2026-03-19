package com.back.backend.global.security.auth;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class CookieJwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_API_KEY = "apiKey";
    public static final String COOKIE_ACCESS_TOKEN = "accessToken";
    public static final String COOKIE_REFRESH_TOKEN = "refreshToken";

    private final JwtTokenService jwtTokenService;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final boolean cookieSecure;

    public CookieJwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            @Value("${security.cookie.secure:false}") boolean cookieSecure
    ) {
        this.jwtTokenService = jwtTokenService;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.cookieSecure = cookieSecure;
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
                || path.startsWith("/api/v1/auth/oauth2/");
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

        // 문서 우선순위: Authorization 헤더 -> 쿠키 폴백
        AuthorizationTokens authTokens = authorizationTokens(request);
        String apiKey = authTokens.apiKey() != null ? authTokens.apiKey() : cookieValue(request, COOKIE_API_KEY);
        String accessToken = authTokens.accessToken() != null ? authTokens.accessToken() : cookieValue(request, COOKIE_ACCESS_TOKEN);
        String refreshToken = cookieValue(request, COOKIE_REFRESH_TOKEN);

        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(accessToken)) {
            apiAuthenticationEntryPoint.commence(request, response, new AuthenticationRequiredException("로그인이 필요합니다."));
            return;
        }

        try {
            authenticateWithAccessToken(accessToken);
            filterChain.doFilter(request, response);
            return;
        } catch (AuthenticationExpiredTokenException expired) {
            // exp 된 accessToken을 refreshToken으로 재발급 후 인증
            if (!StringUtils.hasText(refreshToken)) {
                apiAuthenticationEntryPoint.commence(request, response, expired);
                return;
            }

            try {
                authenticateWithRefreshAndReissue(refreshToken, response);
                filterChain.doFilter(request, response);
            } catch (AuthenticationExpiredTokenException refreshFailed) {
                apiAuthenticationEntryPoint.commence(request, response, refreshFailed);
            }
        } catch (AuthenticationInvalidTokenException invalid) {
            apiAuthenticationEntryPoint.commence(request, response, invalid);
        }
    }

    private void authenticateWithAccessToken(String accessToken) {
        try {
            Jws<Claims> parsed = jwtTokenService.parseAccessToken(accessToken);
            long userId = Long.parseLong(parsed.getBody().getSubject());
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(userId, List.of()));
        } catch (ExpiredJwtException e) {
            throw new AuthenticationExpiredTokenException("세션이 만료되었습니다. 다시 로그인해주세요.", e);
        } catch (JwtException e) {
            throw new AuthenticationInvalidTokenException("유효하지 않은 토큰입니다.", e);
        }
    }

    private void authenticateWithRefreshAndReissue(String refreshToken, HttpServletResponse response) {
        try {
            Jws<Claims> parsed = jwtTokenService.parseRefreshToken(refreshToken);
            long userId = Long.parseLong(parsed.getBody().getSubject());

            String newAccessToken = jwtTokenService.createAccessToken(userId);
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(userId, List.of()));
            setAccessTokenCookie(response, newAccessToken);
        } catch (ExpiredJwtException e) {
            throw new AuthenticationExpiredTokenException("세션이 만료되었습니다. 다시 로그인해주세요.", e);
        } catch (JwtException e) {
            throw new AuthenticationExpiredTokenException("세션이 만료되었습니다. 다시 로그인해주세요.", e);
        }
    }

    private void setAccessTokenCookie(HttpServletResponse response, String newAccessToken) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_ACCESS_TOKEN, newAccessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(jwtTokenService.getAccessTtl())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private AuthorizationTokens authorizationTokens(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader)) {
            return AuthorizationTokens.none();
        }

        // Authorization: Bearer {apiKey} {accessToken}
        if (!authHeader.startsWith("Bearer ")) {
            return AuthorizationTokens.none();
        }

        String afterBearer = authHeader.substring("Bearer ".length()).trim();
        String[] parts = afterBearer.split("\\s+");
        if (parts.length < 2) {
            return AuthorizationTokens.none();
        }

        return new AuthorizationTokens(parts[0], parts[1]);
    }

    private record AuthorizationTokens(String apiKey, String accessToken) {
        static AuthorizationTokens none() {
            return new AuthorizationTokens(null, null);
        }
    }
}

