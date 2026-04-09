package com.back.backend.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 보안 쿠키 생성·삭제를 담당하는 유틸리티 빈.
 * <p>
 * 쿠키 관련 설정(Secure, Domain)을 한 곳에서 관리한다.
 * domain을 설정하는 이유: 백엔드(api.xxx.site)에서 발급한 쿠키를
 * 프론트엔드(www.xxx.site)에서도 사용할 수 있도록 상위 도메인(.xxx.site)으로 지정한다.
 * domain이 비어 있으면 쿠키는 발급한 서브도메인에만 적용된다(로컬 개발 기본동작).
 */
@Component
public class CookieManager {

    private final boolean secure;
    private final String domain;

    public CookieManager(
            @Value("${security.cookie.secure:false}") boolean secure,
            @Value("${security.cookie.domain:}") String domain
    ) {
        this.secure = secure;
        this.domain = domain;
    }

    public void add(HttpServletResponse response, String name, String value, Duration maxAge) {
        response.addHeader("Set-Cookie", build(name, value, maxAge).toString());
    }

    public void clear(HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie", build(name, "", Duration.ZERO).toString());
    }

    public String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private ResponseCookie build(String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge);
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }
}
