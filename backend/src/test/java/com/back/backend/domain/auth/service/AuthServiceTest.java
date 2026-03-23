package com.back.backend.domain.auth.service;

import com.back.backend.domain.auth.exception.UnsupportedAuthProviderException;
import com.back.backend.global.security.apikey.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private ApiKeyService apiKeyService;

    @InjectMocks
    private AuthService authService;

    @Test
    void createAuthorizationStart_buildsAuthorizationUrl() {
        assertThat(authService.createAuthorizationStart(
                "github",
                "https://api.example.com",
                "https://frontend.example.com"
        ))
                .extracting("provider", "authorizationUrl", "state")
                .containsExactly(
                        "github",
                        "https://api.example.com/oauth2/authorization/github?redirectUrl=https%3A%2F%2Ffrontend.example.com",
                        ""
                );
    }

    @Test
    void createAuthorizationStart_throwsWhenProviderUnsupported() {
        assertThatThrownBy(() -> authService.createAuthorizationStart(
                "naver",
                "https://api.example.com",
                "https://frontend.example.com"
        )).isInstanceOf(UnsupportedAuthProviderException.class);
    }

    @Test
    void logout_invalidatesApiKeyWhenPresent() {
        assertThat(authService.logout("api-key")).extracting("loggedOut").isEqualTo(true);
        then(apiKeyService).should().invalidateApiKey("api-key");
    }

    @Test
    void logout_skipsInvalidationWhenApiKeyMissing() {
        assertThat(authService.logout(null)).extracting("loggedOut").isEqualTo(true);
        then(apiKeyService).shouldHaveNoInteractions();
    }
}
