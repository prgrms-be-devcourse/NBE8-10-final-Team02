package com.back.backend.global.security.handler;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.response.ApiErrorResponseWriter;
import com.back.backend.global.security.auth.AuthenticationExpiredTokenException;
import com.back.backend.global.security.auth.AuthenticationInvalidTokenException;
import com.back.backend.global.security.auth.AuthenticationRequiredException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 인증 실패 시 에러 응답을 처리하는 EntryPoint.
 * 필터 체인에서 인증 관련 예외(AuthenticationException)가 발생했을 때
 * 클라이언트에게 규격화된 JSON 에러 메시지를 전달합니다.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public ApiAuthenticationEntryPoint(ApiErrorResponseWriter apiErrorResponseWriter) {
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        ErrorCode code = ErrorCode.AUTH_REQUIRED;
        String message = "로그인이 필요합니다.";
        boolean retryable = false;

        if (authException instanceof AuthenticationExpiredTokenException) {
            code = ErrorCode.AUTH_EXPIRED_TOKEN;
            message = authException.getMessage();
            retryable = true; // 만료된 토큰은 리프레시 후 재시도 가능
        } else if (authException instanceof AuthenticationInvalidTokenException) {
            code = ErrorCode.AUTH_INVALID_TOKEN;
            message = authException.getMessage();
            retryable = true; // 토큰이 잘못된 경우(혹은 중간에 유실된 경우) 재인증 유도
        } else if (authException instanceof AuthenticationRequiredException) {
            code = ErrorCode.AUTH_REQUIRED;
            message = authException.getMessage();
            retryable = false;
        }

        apiErrorResponseWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                code,
                message,
                retryable,
                null
        );
    }
}
