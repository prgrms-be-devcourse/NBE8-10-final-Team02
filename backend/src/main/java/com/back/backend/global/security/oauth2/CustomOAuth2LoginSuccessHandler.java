package com.back.backend.global.security.oauth2;

import com.back.backend.global.security.apikey.ApiKeyService;
import com.back.backend.global.security.jwt.JwtTokenService;
import com.back.backend.global.security.oauth2.app.OAuth2State;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * OAuth2 인증이 최종적으로 성공했을 때 호출되는 핸들러입니다.
 * <p>
 * 주요 역할:
 * 1. 인증된 사용자 정보를 바탕으로 JWT AccessToken과 ApiKey를 생성합니다.
 * 2. 생성된 토큰들을 보안 쿠키(HttpOnly)에 저장하여 클라이언트에 전달합니다.
 * 3. 로그인 시작 시 저장했던 state 파라미터를 복원하여 원래의 리다이렉트 경로로 이동시킵니다.
 */
@Component
public class CustomOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenService jwtTokenService;
    private final ApiKeyService apiKeyService;
    private final boolean cookieSecure;
    private final String defaultRedirectUrl;

    /**
     * @param cookieSecure HTTPS 환경 여부에 따른 쿠키 Secure 설정 값
     * @param defaultRedirectUrl state 정보가 없을 경우 이동할 기본 프론트엔드 주소
     */
    public CustomOAuth2LoginSuccessHandler(
            JwtTokenService jwtTokenService,
            ApiKeyService apiKeyService,
            @Value("${security.cookie.secure:false}") boolean cookieSecure,
            @Value("${security.oauth2.frontend-redirect-url:http://localhost:3000}") String defaultRedirectUrl
    ) {
        this.jwtTokenService = jwtTokenService;
        this.apiKeyService = apiKeyService;
        this.cookieSecure = cookieSecure;
        this.defaultRedirectUrl = defaultRedirectUrl;
    }

    /**
     * 인증 성공 직후 실행되는 로직입니다.
     * * @param authentication 인증된 유저 정보(OurOAuth2User)를 포함하고 있는 객체
     */
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        // CustomOAuth2UserService에서 생성하여 반환했던 우리측 유저 객체를 꺼냅니다
        OurOAuth2User ourUser = (OurOAuth2User) authentication.getPrincipal();
        long userId = ourUser.getUserId();

        String accessToken = jwtTokenService.createAccessToken(userId);
        String apiKey = apiKeyService.createApiKey(userId);

        // 생성된 열쇠들을 브라우저 쿠키에 저장합니다.
        response.addHeader("Set-Cookie", buildCookie("apiKey", apiKey, Duration.ofDays(30)));
        response.addHeader("Set-Cookie", buildCookie("accessToken", accessToken, jwtTokenService.getAccessTtl()));

        // 로그인 시작 시 CustomOAuth2AuthorizationRequestResolver에서 담았던 state를 확인합니다.
        String stateParam = request.getParameter("state");
        String redirectUrl = defaultRedirectUrl;
        if (stateParam != null && !stateParam.isBlank()) {
            try {
                redirectUrl = OAuth2State.decode(stateParam).getRedirectUrl();
            } catch (Exception ignored) {
                // state 디코딩 실패 시 기본값 사용
            }
        }

        // 모든 처리가 완료되면 프론트엔드의 원래 페이지로 사용자를 이동시킵니다.
        response.sendRedirect(redirectUrl);
    }

    /**
     * 보안 설정이 적용된 쿠키 문자열을 생성합니다.
     * * @param name 쿠키 키값 (apiKey, accessToken 등)
     * @param value 저장할 토큰 값
     * @param maxAge 쿠키 유효 기간
     * @return 생성된 Set-Cookie 헤더 값
     */
    private String buildCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
