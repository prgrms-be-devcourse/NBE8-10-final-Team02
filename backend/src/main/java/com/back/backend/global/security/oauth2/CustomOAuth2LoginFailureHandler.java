package com.back.backend.global.security.oauth2;

import com.back.backend.global.security.oauth2.app.OAuth2State;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 인증 실패(예: GitHub 인증 화면에서 "Cancel" 또는 연동 실패) 시 프론트엔드 로그인 페이지로 리다이렉트합니다.
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

        String frontendBase = defaultFrontendUrl;
        String originalRedirect = null;
        Long linkUserId = null;

        String stateParam = request.getParameter("state");
        if (stateParam != null && !stateParam.isBlank()) {
            try {
                OAuth2State state = OAuth2State.decode(stateParam);
                originalRedirect = state.getRedirectUrl();
                linkUserId = state.getLinkUserId();

                if (originalRedirect != null && !originalRedirect.isBlank() && !originalRedirect.equals("/")) {
                    // 외부 URL(http...)인 경우 해당 base를 사용
                    if (originalRedirect.startsWith("http")) {
                        java.net.URI uri = java.net.URI.create(originalRedirect);
                        if (uri.getScheme() != null && uri.getHost() != null) {
                            int port = uri.getPort();
                            frontendBase = uri.getScheme() + "://" + uri.getHost()
                                    + (port > 0 ? ":" + port : "");
                        }
                    }
                }
            } catch (Exception ignored) {
                // 디코딩 실패 시 defaultFrontendUrl 사용
            }
        }

        // 연동 흐름(linkUserId 존재) 실패 시 원래 페이지로 에러 메시지와 함께 리다이렉트
        if (linkUserId != null && originalRedirect != null && !originalRedirect.isBlank()) {
            String msg = extractErrorMessage(exception);
            String encoded = URLEncoder.encode(msg, StandardCharsets.UTF_8);

            // originalRedirect가 절대 경로라면 그대로 사용, 상대 경로라면 base를 붙임
            String redirectUrl = originalRedirect.startsWith("http")
                    ? originalRedirect
                    : frontendBase + (originalRedirect.startsWith("/") ? "" : "/") + originalRedirect;

            // 이미 쿼리 파라미터가 있는지 확인 후 추가
            String separator = redirectUrl.contains("?") ? "&" : "?";
            response.sendRedirect(redirectUrl + separator + "error=" + encoded);
            return;
        }

        // 일반 로그인 실패 시
        // **'기존 계정에 소셜을 연동(Linking)하려는 목적이 아닌, 순수하게 서비스에 진입하기 위한 최초의 OAuth2 로그인 시도'**를 구분
        // GitHub 인증 창에서 "Cancel"을 누르고 돌아왔을 때, 백엔드에서 사용자에게 흰 화면이나 JSON 에러를 보여줄 수는 없으니, 프론트엔드의 그 '시작 페이지'로 돌려보내기 위해
        String errorMsg = extractErrorMessage(exception);
        String encodedMsg = URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
        response.sendRedirect(frontendBase + "/login?error=" + encodedMsg);
    }

    private String extractErrorMessage(AuthenticationException exception) {
        // OAuth2AuthenticationException인 경우 내부 OAuth2Error의 설명을 우선 확인
        if (exception instanceof OAuth2AuthenticationException oae) {
            if (oae.getError() != null && oae.getError().getDescription() != null) {
                return oae.getError().getDescription();
            }
        }

        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }

        String msg = exception.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg;
        }

        return "인증 중 오류가 발생했습니다.";
    }
}
