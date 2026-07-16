package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.exception.ErrorCode;

/**
 * Error envelope — matches the contract the Angular frontend expects:
 * <pre>{ "success": false, "error": "...", "code": "...", "details"?: ... }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(boolean success, String error, String code, Object details) {

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(false, message, code.name(), null);
    }

    public static ApiErrorResponse of(ErrorCode code, String message, Object details) {
        return new ApiErrorResponse(false, message, code.name(), details);
    }
}
