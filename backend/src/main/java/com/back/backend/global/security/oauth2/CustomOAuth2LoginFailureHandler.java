package com.back.backend.global.security.oauth2;

import com.back.backend.global.security.oauth2.app.OAuth2State;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 인증 실패(예: GitHub 인증 화면에서 "Cancel") 시 프론트엔드 로그인 페이지로 리다이렉트합니다.
 *
 * <p>Spring Security의 기본 실패 처리는 백엔드의 {@code /login?error}로 리다이렉트해 JSON 에러 응답을 노출합니다.
 * 이 핸들러는 그 대신 프론트엔드 로그인 페이지로 사용자를 안전하게 돌려보냅니다.
 */
@Component
public class CustomOAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2LoginFailureHandler.class);

    private final String defaultFrontendUrl;

    public CustomOAuth2LoginFailureHandler(
            @Value("${security.oauth2.frontend-redirect-url:http://localhost:3000}") String defaultFrontendUrl
    ) {
        this.defaultFrontendUrl = defaultFrontendUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        log.warn("OAuth2 authentication failed: {}", exception.getMessage());

        // state 파라미터에서 프론트엔드 redirectUrl을 복원한다.
        // GitHub이 cancel 시 돌려보내는 URL 예시:
        //   /login/oauth2/code/github?error=access_denied&state=<encoded>
        String frontendBase = defaultFrontendUrl;
        String stateParam = request.getParameter("state");
        if (stateParam != null && !stateParam.isBlank()) {
            try {
                String originalRedirect = OAuth2State.decode(stateParam).getRedirectUrl();
                // redirectUrl은 성공 시 이동할 페이지이므로, 실패 시에는 그 origin만 추출한다.
                if (originalRedirect != null && !originalRedirect.isBlank() && !originalRedirect.equals("/")) {
                    java.net.URI uri = java.net.URI.create(originalRedirect);
                    if (uri.getScheme() != null && uri.getHost() != null) {
                        int port = uri.getPort();
                        frontendBase = uri.getScheme() + "://" + uri.getHost()
                                + (port > 0 ? ":" + port : "");
                    }
                }
            } catch (Exception ignored) {
                // 디코딩 실패 시 defaultFrontendUrl 사용
            }
        }

        response.sendRedirect(frontendBase + "/login?error=cancelled");
    }
}