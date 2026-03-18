package com.back.backend.global.security.handler;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.response.ApiErrorResponseWriter;
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
        apiErrorResponseWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_REQUIRED,
                "로그인이 필요합니다.",
                false,
                null
        );
    }
}
