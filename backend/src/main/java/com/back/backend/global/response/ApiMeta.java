package com.back.backend.global.response;

import com.back.backend.global.request.RequestIdContext;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(
        String requestId,
        Instant timestamp,
        Pagination pagination
) {

    public static ApiMeta fromCurrentRequest() {
        return new ApiMeta(RequestIdContext.currentRequestId(), Instant.now(), null);
    }

    public static ApiMeta fromCurrentRequest(Pagination pagination) {
        return new ApiMeta(RequestIdContext.currentRequestId(), Instant.now(), pagination);
    }
}
