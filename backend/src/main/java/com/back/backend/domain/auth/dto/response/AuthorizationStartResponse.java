package com.back.backend.domain.auth.dto.response;

import com.back.backend.domain.auth.entity.AuthProvider;

public record AuthorizationStartResponse(
        String provider,
        String authorizationUrl,
        String state
) {

    public static AuthorizationStartResponse of(AuthProvider provider, String authorizationUrl, String state) {
        return new AuthorizationStartResponse(provider.getValue(), authorizationUrl, state);
    }
}
