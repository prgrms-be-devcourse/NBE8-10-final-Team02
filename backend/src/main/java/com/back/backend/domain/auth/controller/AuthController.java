package com.back.backend.domain.auth.controller;

import com.back.backend.domain.auth.dto.response.AuthorizationStartResponse;
import com.back.backend.domain.auth.dto.response.LogoutResponse;
import com.back.backend.domain.auth.service.AuthService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.CookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieManager cookieManager;
    @Value("${security.oauth2.frontend-redirect-url:http://localhost:3000}")
    private String frontendRedirectUrl;
    @Value("${security.oauth2.backend-base-url:}")
    private String backendBaseUrl;

    @GetMapping("/oauth2/{provider}/authorize")
    public ApiResponse<AuthorizationStartResponse> getAuthorizeUrl(
            @PathVariable String provider,
            HttpServletRequest request
    ) {
        String resolvedBackendBaseUrl = (backendBaseUrl != null && !backendBaseUrl.isBlank())
                ? backendBaseUrl
                : buildBackendBaseUrl(request);

        return ApiResponse.success(
                authService.createAuthorizationStart(provider, resolvedBackendBaseUrl, frontendRedirectUrl)
        );
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(
            @CookieValue(name = "apiKey", required = false) String apiKey,
            HttpServletResponse response
    ) {
        cookieManager.clear(response, "apiKey");
        cookieManager.clear(response, "accessToken");
        cookieManager.clear(response, "refreshToken");

        return ApiResponse.success(authService.logout(apiKey));
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
