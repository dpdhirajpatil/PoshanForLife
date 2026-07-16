package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Success envelope — matches the contract the Angular frontend expects:
 * <pre>{ "success": true, "data": ..., "meta"?: { "total", "page", "limit" } }</pre>
 * Errors use {@link ApiErrorResponse}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, PageMeta meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, meta);
    }

    public static <T> ApiResponse<T> ok(T data, long total, int page, int limit) {
        return new ApiResponse<>(true, data, new PageMeta(total, page, limit));
    }
}
