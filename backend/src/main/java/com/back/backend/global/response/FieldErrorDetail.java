package com.back.backend.global.response;

public record FieldErrorDetail(
        String field,
        String reason
) {
}
