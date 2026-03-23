package com.back.backend.domain.auth.service;

import com.back.backend.domain.auth.dto.response.AuthorizationStartResponse;
import com.back.backend.domain.auth.dto.response.LogoutResponse;
import com.back.backend.domain.auth.entity.AuthProvider;
import com.back.backend.domain.auth.exception.UnsupportedAuthProviderException;
import com.back.backend.global.security.apikey.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ApiKeyService apiKeyService;

    public AuthorizationStartResponse createAuthorizationStart(
            String providerValue,
            String backendBaseUrl,
            String frontendRedirectUrl
    ) {
        AuthProvider provider = parseProvider(providerValue);
        String encodedRedirectUrl = URLEncoder.encode(frontendRedirectUrl, StandardCharsets.UTF_8);
        String authorizationUrl = backendBaseUrl
                + "/oauth2/authorization/"
                + provider.getValue()
                + "?redirectUrl="
                + encodedRedirectUrl;

        return AuthorizationStartResponse.of(provider, authorizationUrl, "");
    }

    public LogoutResponse logout(String apiKey) {
        if (StringUtils.hasText(apiKey)) {
            apiKeyService.invalidateApiKey(apiKey);
        }

        return new LogoutResponse(true);
    }

    private AuthProvider parseProvider(String providerValue) {
        try {
            return AuthProvider.fromRegistrationId(providerValue);
        } catch (IllegalArgumentException exception) {
            throw new UnsupportedAuthProviderException(providerValue);
        }
    }
}
