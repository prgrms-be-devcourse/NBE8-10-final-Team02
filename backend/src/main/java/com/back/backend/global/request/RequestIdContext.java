package com.back.backend.global.request;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

public final class RequestIdContext {

    public static final String ATTRIBUTE_NAME = "requestId";
    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String currentRequestId() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();

            Object requestIdAttribute = request.getAttribute(ATTRIBUTE_NAME);
            if (requestIdAttribute instanceof String requestId && StringUtils.hasText(requestId)) {
                return requestId;
            }

            String requestIdHeader = request.getHeader(HEADER_NAME);
            if (StringUtils.hasText(requestIdHeader)) {
                return requestIdHeader;
            }
        }

        String mdcRequestId = MDC.get(MDC_KEY);
        if (StringUtils.hasText(mdcRequestId)) {
            return mdcRequestId;
        }

        return generate();
    }

    public static String generate() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
