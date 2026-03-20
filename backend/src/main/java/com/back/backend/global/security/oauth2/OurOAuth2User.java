package com.back.backend.global.security.oauth2;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Spring Security OAuth2 인증 이후 내부 userId를 함께 전달하기 위한 래퍼 클래스.
 * CustomOAuth2UserService → CustomOAuth2LoginSuccessHandler로 userId를 전달하는 데 사용한다.
 */
public class OurOAuth2User implements OAuth2User {

    private final long userId;
    private final OAuth2User delegate;

    public OurOAuth2User(long userId, OAuth2User delegate) {
        this.userId = userId;
        this.delegate = delegate;
    }

    public long getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}