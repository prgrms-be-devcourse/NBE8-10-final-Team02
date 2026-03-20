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
     * 내부 생성자
     * * @param redirectUrl 인증 성공 후 이동할 프론트엔드 경로
     * @param originState 보안 검증을 위한 랜덤 고유 문자열
     */
    private OAuth2State(String redirectUrl, String originState) {
        this.redirectUrl = redirectUrl;
        this.originState = originState;
    }

    /**
     * @return 저장된 리다이렉트 URL 반환
     */
    public String getRedirectUrl() {
        return redirectUrl;
    }

    /**
     * 현재 객체의 정보를 URL 안전한 Base64 문자열로 인코딩합니다.
     * <p>
     * 예: "/posts/1#uuid-string" -> "L3Bvc3RzLzEj... (Base64)"
     * * @return 인코딩된 state 문자열
     */
    public String encode() {
        String raw = redirectUrl + DELIMITER + originState;
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 리다이렉트 URL을 받아 새로운 OAuth2State 객체를 생성합니다.
     * <p>
     * 생성 시 보안을 위한 무작위 UUID를 자동으로 생성하여 포함합니다.
     * * @param redirectUrl 프론트엔드에서 전달받은 복귀 경로
     * @return 생성된 OAuth2State 인스턴스
     */
    public static OAuth2State of(String redirectUrl) {
        String url = (redirectUrl != null && !redirectUrl.isBlank()) ? redirectUrl : DEFAULT_REDIRECT_URL;
        return new OAuth2State(url, UUID.randomUUID().toString());
    }

    /**
     * 인코딩되어 돌아온 state 문자열을 다시 객체 형태로 복원(디코딩)합니다.
     * <p>
     * 예외 발생 시 서비스 중단을 막기 위해 기본값(/)을 가진 객체를 반환합니다.
     * * @param encoded 소셜 공급자로부터 전달받은 인코딩된 문자열
     * @return 복원된 OAuth2State 객체
     */
    public static OAuth2State decode(String encoded) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split(DELIMITER, 2);
            String url = (parts.length > 0 && !parts[0].isBlank()) ? parts[0] : DEFAULT_REDIRECT_URL;
            String state = parts.length > 1 ? parts[1] : "";
            return new OAuth2State(url, state);
        } catch (Exception e) {
            return new OAuth2State(DEFAULT_REDIRECT_URL, "");
        }
    }
}
