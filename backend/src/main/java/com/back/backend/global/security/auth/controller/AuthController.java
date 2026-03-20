package com.back.backend.global.security.auth.controller;

import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.global.security.auth.entity.AuthProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ApiKeyService apiKeyService;
    private final boolean cookieSecure;
    private final String frontendRedirectUrl;

    public AuthController(
            ApiKeyService apiKeyService,
            @Value("${security.cookie.secure:false}") boolean cookieSecure,
            @Value("${security.oauth2.frontend-redirect-url:http://localhost:3000}") String frontendRedirectUrl
    ) {
        this.apiKeyService = apiKeyService;
        this.cookieSecure = cookieSecure;
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    /**
     * OAuth2 로그인 시작 URL 반환.
     * 반환된 authorizationUrl로 브라우저를 이동시키면 Spring Security가 OAuth2 흐름을 시작한다.
     */
    @GetMapping("/oauth2/{provider}/authorize")
    public ApiResponse<Map<String, String>> getAuthorizeUrl(
            @PathVariable String provider,
            @RequestParam(required = false) String redirectUrl,
            HttpServletRequest request
    ) {
        AuthProvider.fromRegistrationId(provider); // provider 유효성 검증

        String effectiveRedirectUrl = (redirectUrl != null && !redirectUrl.isBlank())
                ? redirectUrl
                : frontendRedirectUrl;

        String backendBaseUrl = buildBackendBaseUrl(request);
        String encodedRedirectUrl = URLEncoder.encode(effectiveRedirectUrl, StandardCharsets.UTF_8);
        String authorizationUrl = backendBaseUrl + "/oauth2/authorization/" + provider
                + "?redirectUrl=" + encodedRedirectUrl;

        return ApiResponse.success(Map.of(
                "provider", provider,
                "authorizationUrl", authorizationUrl,
                "state", ""
        ));
    }

    /**
     * 로그아웃: Redis에서 API Key를 무효화하고 쿠키를 삭제한다.
     */
    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(
            @CookieValue(name = "apiKey", required = false) String apiKey,
            HttpServletResponse response
    ) {
        if (apiKey != null) {
            apiKeyService.invalidateApiKey(apiKey);
        }
        clearCookie(response, "apiKey");
        clearCookie(response, "accessToken");
        clearCookie(response, "refreshToken");

        return ApiResponse.success(Map.of("message", "로그아웃 성공"));
    }

    private String buildBackendBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        boolean isDefaultPort = (scheme.equals("http") && serverPort == 80)
                || (scheme.equals("https") && serverPort == 443);
        return isDefaultPort
                ? scheme + "://" + serverName
                : scheme + "://" + serverName + ":" + serverPort;
    }

    private void clearCookie(HttpServletResponse response, String name) {
        String cleared = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build()
                .toString();
        response.addHeader("Set-Cookie", cleared);
    }
}