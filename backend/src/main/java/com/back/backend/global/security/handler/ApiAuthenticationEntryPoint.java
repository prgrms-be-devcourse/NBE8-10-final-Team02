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

        if (authException instanceof AuthenticationExpiredTokenException) {
            code = ErrorCode.AUTH_EXPIRED_TOKEN;
            message = authException.getMessage();
        } else if (authException instanceof AuthenticationInvalidTokenException) {
            code = ErrorCode.AUTH_INVALID_TOKEN;
            message = authException.getMessage();
        } else if (authException instanceof AuthenticationRequiredException) {
            code = ErrorCode.AUTH_REQUIRED;
            message = authException.getMessage();
        }

        apiErrorResponseWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                code,
                message,
                false,
                null
        );
    }
}
