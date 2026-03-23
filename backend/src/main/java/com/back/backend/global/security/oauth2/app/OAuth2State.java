package com.back.backend.global.security.oauth2.app;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * OAuth2 인증 흐름 중 보안 검증(state)과 인증 후 이동 경로(redirectUrl)를 관리하는 유틸리티 클래스입니다.
 * <p>
 * {@code redirectUrl#UUID} 형태의 문자열을 Base64로 인코딩하여 소셜 로그인 공급자에게 전달하며,
 * 복귀 시 이를 디코딩하여 사용자를 원래 페이지로 안전하게 이동시킵니다.
 */
public class OAuth2State {

    private static final String DELIMITER = "#";
    private static final String DEFAULT_REDIRECT_URL = "/";

    private final String redirectUrl;
    private final String originState;
    /**
     * null이면 일반 로그인 흐름.
     * 값이 있으면 이미 로그인된 사용자가 GitHub 계정을 추가 연동하는 흐름.
     */
    private final Long linkUserId;

    private OAuth2State(String redirectUrl, String originState, Long linkUserId) {
        this.redirectUrl = redirectUrl;
        this.originState = originState;
        this.linkUserId = linkUserId;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    /** null이면 일반 로그인, 값이 있으면 계정 연동 흐름 */
    public Long getLinkUserId() {
        return linkUserId;
    }

    /**
     * 인코딩: "redirectUrl#UUID" (일반) 또는 "redirectUrl#UUID#userId" (연동)
     */
    public String encode() {
        String raw = redirectUrl + DELIMITER + originState
                + (linkUserId != null ? DELIMITER + linkUserId : "");
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 일반 로그인용 state 생성 */
    public static OAuth2State of(String redirectUrl) {
        String url = (redirectUrl != null && !redirectUrl.isBlank()) ? redirectUrl : DEFAULT_REDIRECT_URL;
        return new OAuth2State(url, UUID.randomUUID().toString(), null);
    }

    /** GitHub 계정 연동용 state 생성 — linkUserId: 연동 대상 app 사용자 ID */
    public static OAuth2State ofLink(String redirectUrl, Long linkUserId) {
        String url = (redirectUrl != null && !redirectUrl.isBlank()) ? redirectUrl : DEFAULT_REDIRECT_URL;
        return new OAuth2State(url, UUID.randomUUID().toString(), linkUserId);
    }

    /**
     * 디코딩: 파트 3개(redirectUrl, UUID, linkUserId)까지 지원.
     * linkUserId가 없으면 null 반환 (일반 로그인).
     */
    public static OAuth2State decode(String encoded) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split(DELIMITER, 3);
            String url = (parts.length > 0 && !parts[0].isBlank()) ? parts[0] : DEFAULT_REDIRECT_URL;
            String state = parts.length > 1 ? parts[1] : "";
            Long linkUserId = (parts.length > 2 && !parts[2].isBlank()) ? Long.parseLong(parts[2]) : null;
            return new OAuth2State(url, state, linkUserId);
        } catch (Exception e) {
            return new OAuth2State(DEFAULT_REDIRECT_URL, "", null);
        }
    }
}
