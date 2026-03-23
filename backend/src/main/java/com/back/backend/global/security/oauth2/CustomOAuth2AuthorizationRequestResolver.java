package com.back.backend.global.security.oauth2;

import com.back.backend.global.security.oauth2.app.OAuth2State;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 인증 요청 시 기본 동작을 커스텀하기 위한 리졸버(Resolver) 클래스입니다.
 * <p>
 * 주요 역할: 사용자가 소셜 로그인을 시작할 때, 프론트엔드에서 전달한 {@code redirectUrl} 파라미터를
 * OAuth2 표준 파라미터인 {@code state}에 포함시켜 인증 서버(GitHub, Google 등)로 전달합니다.
 * 이 과정을 통해 로그인이 완료된 후 사용자를 원래 있던 페이지로 정확히 돌려보낼 수 있습니다.
 */
@Component
public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public CustomOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest req = delegate.resolve(request);
        return req != null ? customizeState(req, request) : null;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest req = delegate.resolve(request, clientRegistrationId);
        return req != null ? customizeState(req, request) : null;
    }

    private OAuth2AuthorizationRequest customizeState(OAuth2AuthorizationRequest req, HttpServletRequest request) {
        String redirectUrl = request.getParameter("redirectUrl");
        String linkUserIdStr = request.getParameter("linkUserId");
        Long linkUserId = null;
        if (linkUserIdStr != null && !linkUserIdStr.isBlank()) {
            try { linkUserId = Long.parseLong(linkUserIdStr); } catch (NumberFormatException ignored) {}
        }
        OAuth2State state = (linkUserId != null)
                ? OAuth2State.ofLink(redirectUrl != null ? redirectUrl : "", linkUserId)
                : OAuth2State.of(redirectUrl != null ? redirectUrl : "");
        return OAuth2AuthorizationRequest.from(req)
                .state(state.encode())
                .build();
    }
}
