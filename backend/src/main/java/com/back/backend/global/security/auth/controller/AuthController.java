package com.back.backend.global.security.auth.controller;

import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.CookieManager;
import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.global.security.auth.entity.AuthProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ApiKeyService apiKeyService;
    private final CookieManager cookieManager;
    private final String frontendRedirectUrl;
    private final String backendBaseUrl;

    public AuthController(
            ApiKeyService apiKeyService,
            CookieManager cookieManager,
            @Value("${security.oauth2.frontend-redirect-url:http://localhost:3000}") String frontendRedirectUrl,
            @Value("${security.oauth2.backend-base-url:}") String backendBaseUrl
    ) {
        this.apiKeyService = apiKeyService;
        this.cookieManager = cookieManager;
        this.frontendRedirectUrl = frontendRedirectUrl;
        this.backendBaseUrl = backendBaseUrl;
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

        // [주의] authorizationUrl은 브라우저가 직접 이동할 URL이므로 반드시 공개 도메인이어야 한다.
        // 이 요청은 브라우저 → Next.js(fe 컨테이너) → 백엔드(be 컨테이너) 순으로 프록시된다.
        // Next.js는 --network-alias backend 덕분에 Docker 내부 네트워크에서 http://backend:8080 으로 백엔드에 접근하므로,
        // request.getServerName()은 "backend"를 반환한다.
        // 이를 그대로 사용하면 authorizationUrl이 http://backend:8080/oauth2/... 가 되어
        // 브라우저에서 DNS_PROBE_FINISHED_NXDOMAIN 오류가 발생한다. (Docker 외부에서는 "backend" 도메인을 알 수 없음)
        // 따라서 SECURITY_OAUTH2_BACKEND_BASE_URL 환경변수로 공개 도메인을 명시적으로 주입해야 한다.
        String backendBaseUrl = (this.backendBaseUrl != null && !this.backendBaseUrl.isBlank())
                ? this.backendBaseUrl
                : buildBackendBaseUrl(request);
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
        cookieManager.clear(response, "apiKey");
        cookieManager.clear(response, "accessToken");
        cookieManager.clear(response, "refreshToken");

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
}
