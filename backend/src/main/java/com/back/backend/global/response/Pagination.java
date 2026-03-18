package com.back.backend.global.response;

public record Pagination(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
