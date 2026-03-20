package com.back.backend.global.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Base64;

/**
 * OAuth2 인증 요청(Authorization Request) 정보를 세션이 아닌 쿠키에 저장하는 리포지토리입니2다.
 * <p>
 * JWT 기반의 Stateless 서버에서는 서버 세션을 사용할 수 없으므로,
 * 인증 과정 중 필요한 임시 데이터(state, PKCE 등)를 브라우저의 HttpOnly 쿠키에 직렬화하여 보관합니다.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final Duration COOKIE_MAX_AGE = Duration.ofMinutes(5);

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String value = getCookieValue(request, COOKIE_NAME);
        return value != null ? deserialize(value) : null;
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            clearCookie(response, COOKIE_NAME);
            return;
        }
        addCookie(response, COOKIE_NAME, serialize(authorizationRequest), COOKIE_MAX_AGE);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest req = loadAuthorizationRequest(request);
        clearCookie(response, COOKIE_NAME);
        return req;
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(request);
            return Base64.getUrlEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest 직렬화 실패", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (OAuth2AuthorizationRequest) ois.readObject();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        response.addHeader("Set-Cookie",
                ResponseCookie.from(name, value)
                        .httpOnly(true)
                        .path("/")
                        .maxAge(maxAge)
                        .sameSite("Lax")
                        .build()
                        .toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie",
                ResponseCookie.from(name, "")
                        .httpOnly(true)
                        .path("/")
                        .maxAge(Duration.ZERO)
                        .sameSite("Lax")
                        .build()
                        .toString());
    }
}
