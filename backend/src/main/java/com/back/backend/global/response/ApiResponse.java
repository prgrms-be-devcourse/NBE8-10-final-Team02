package com.back.backend.global.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiMeta meta
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, ApiMeta.fromCurrentRequest());
    }

    public static <T> ApiResponse<T> success(T data, Pagination pagination) {
        return new ApiResponse<>(true, data, ApiMeta.fromCurrentRequest(pagination));
    }
}
