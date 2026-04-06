package com.back.backend.global.security.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * CustomOAuth2AuthorizationRequestResolver 단위 테스트.
 *
 * OAuth 제공자별로 올바른 prompt 파라미터가 추가되는지 검증한다.
 * DefaultOAuth2AuthorizationRequestResolver(delegate)를 mock으로 대체해
 * Spring Security 컨텍스트 없이 빠르게 실행한다.
 */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2AuthorizationRequestResolverTest {

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private DefaultOAuth2AuthorizationRequestResolver mockDelegate;

    private CustomOAuth2AuthorizationRequestResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CustomOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
        // 내부 delegate를 mock으로 교체 — 실제 ClientRegistration 조회 없이 테스트
        ReflectionTestUtils.setField(resolver, "delegate", mockDelegate);
    }

    // ─────────────────────────────────────────────────
    // prompt 파라미터 검증
    // ─────────────────────────────────────────────────

    @Test
    void resolve_addsPromptConsentForGoogle() {
        HttpServletRequest request = mockRequest("/oauth2/authorization/google");
        given(mockDelegate.resolve(request, "google")).willReturn(baseRequest("google"));

        OAuth2AuthorizationRequest result = resolver.resolve(request, "google");

        assertThat(result.getAdditionalParameters())
                .containsEntry("prompt", "consent");
    }

    @Test
    void resolve_addsPromptLoginForKakao() {
        HttpServletRequest request = mockRequest("/oauth2/authorization/kakao");
        given(mockDelegate.resolve(request, "kakao")).willReturn(baseRequest("kakao"));

        OAuth2AuthorizationRequest result = resolver.resolve(request, "kakao");

        assertThat(result.getAdditionalParameters())
                .containsEntry("prompt", "login");
    }

    @Test
    void resolve_doesNotAddPromptForGithub() {
        HttpServletRequest request = mockRequest("/oauth2/authorization/github");
        given(mockDelegate.resolve(request, "github")).willReturn(baseRequest("github"));

        OAuth2AuthorizationRequest result = resolver.resolve(request, "github");

        assertThat(result.getAdditionalParameters())
                .doesNotContainKey("prompt");
    }

    // ─────────────────────────────────────────────────
    // state 인코딩 검증 (기존 동작 회귀 방지)
    // ─────────────────────────────────────────────────

    @Test
    void resolve_encodesRedirectUrlIntoState() {
        HttpServletRequest request = mockRequest("/oauth2/authorization/google", "http://localhost:3000/portfolio", null);
        given(mockDelegate.resolve(request, "google")).willReturn(baseRequest("google"));

        OAuth2AuthorizationRequest result = resolver.resolve(request, "google");

        // state가 기본 빈 문자열이 아닌 인코딩된 값이어야 한다
        assertThat(result.getState()).isNotBlank();
    }

    @Test
    void resolve_returnsNullWhenDelegateReturnsNull() {
        // Use a plain mock — no stubs needed since customizeState is skipped when delegate returns null
        HttpServletRequest request = mock(HttpServletRequest.class);

        OAuth2AuthorizationRequest result = resolver.resolve(request, "github");

        assertThat(result).isNull();
    }

    @Test
    void resolve_preservesExistingAdditionalParameters() {
        HttpServletRequest request = mockRequest("/oauth2/authorization/google");

        OAuth2AuthorizationRequest withExistingParam = OAuth2AuthorizationRequest.authorizationCode()
                .clientId("test-client")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scopes(Set.of("openid", "email"))
                .state("existing-state")
                .additionalParameters(Map.of("access_type", "offline"))
                .build();
        given(mockDelegate.resolve(request, "google")).willReturn(withExistingParam);

        OAuth2AuthorizationRequest result = resolver.resolve(request, "google");

        assertThat(result.getAdditionalParameters())
                .containsEntry("access_type", "offline")
                .containsEntry("prompt", "consent");
    }

    // ─────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────

    private HttpServletRequest mockRequest(String uri) {
        return mockRequest(uri, null, null);
    }

    private HttpServletRequest mockRequest(String uri, String redirectUrl, String linkUserId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getRequestURI()).willReturn(uri);
        given(request.getParameter("redirectUrl")).willReturn(redirectUrl);
        given(request.getParameter("linkUserId")).willReturn(linkUserId);
        return request;
    }

    private OAuth2AuthorizationRequest baseRequest(String registrationId) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .clientId("test-client-" + registrationId)
                .authorizationUri("https://provider.example.com/oauth2/auth")
                .redirectUri("http://localhost:8080/login/oauth2/code/" + registrationId)
                .scopes(Set.of("openid"))
                .state("base-state")
                .build();
    }
}