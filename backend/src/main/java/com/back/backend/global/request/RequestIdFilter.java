package com.back.backend.global.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request);

        request.setAttribute(RequestIdContext.ATTRIBUTE_NAME, requestId);
        response.setHeader(RequestIdContext.HEADER_NAME, requestId);
        MDC.put(RequestIdContext.MDC_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIdContext.MDC_KEY);
        }
    }

    private String requestId(HttpServletRequest request) {
        String requestIdFromHeader = request.getHeader(RequestIdContext.HEADER_NAME);

        if (StringUtils.hasText(requestIdFromHeader)) {
            return requestIdFromHeader.trim();
        }

        return RequestIdContext.generate();
    }
}
