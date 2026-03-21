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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// @Component : filter 이중등록문제를 막기위해 제거
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
                || path.startsWith("/api/v1/auth/oauth2/")
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

        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(accessToken)) {
            apiAuthenticationEntryPoint.commence(request, response, new AuthenticationRequiredException("로그인이 필요합니다."));
            return;
        }

        // API Key 검증 (Redis)
        Long apiKeyUserId = apiKeyService.validateAndGetUserId(apiKey);
        if (apiKeyUserId == null) {
            apiAuthenticationEntryPoint.commence(request, response, new AuthenticationInvalidTokenException("유효하지 않은 API Key입니다."));
            return;
        }

        try {
            long accessTokenUserId = authenticateWithAccessToken(accessToken);

            // API Key의 userId와 Access Token의 userId가 일치하는지 검증
            if (!apiKeyUserId.equals(accessTokenUserId)) {
                apiAuthenticationEntryPoint.commence(request, response, new AuthenticationInvalidTokenException("API Key와 토큰이 일치하지 않습니다."));
                return;
            }
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

    /**
     * Access Token을 검증하고 Spring Security Context에 인증 정보를 등록합니다.
     * * @param accessToken 검증할 JWT 액세스 토큰
     * @return userId 토큰에서 추출한 사용자 ID
     * @throws AuthenticationExpiredTokenException 토큰이 만료된 경우 발생
     * @throws AuthenticationInvalidTokenException 토큰이 변조되었거나 형식이 잘못된 경우 발생
     */
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
            throw new AuthenticationInvalidTokenException("유효하지 않은 리프레시 토큰입니다.", e);
        }
    }

    private void setAccessTokenCookie(HttpServletResponse response, String newAccessToken) {
        cookieManager.add(response, COOKIE_ACCESS_TOKEN, newAccessToken, jwtTokenService.getAccessTtl());
    }

    /**
     * HTTP 요청의 쿠키 목록에서 특정 이름({@code name})에 해당하는 값을 추출합니다.
     * * @param request HTTP 요청 객체
     * @param name 찾고자 하는 쿠키의 이름 (예: "access_token")
     * @return 쿠키의 값(Value), 해당 이름의 쿠키가 없거나 목록이 비어있으면 null 반환
     */
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

    /**
     * HTTP 요청 헤더에서 인증 토큰 세트(API Key, Access Token)를 추출합니다.
     * * 형식: {@code Authorization: Bearer {apiKey} {accessToken}}
     * * @param request HTTP 요청 객체
     * @return 추출된 인증 토큰 객체 (누락되거나 형식이 틀리면 빈 객체 반환)
     */
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

