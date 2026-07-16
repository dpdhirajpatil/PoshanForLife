package com.poshanforlife.api.exception;

import lombok.Getter;

/**
 * Base class for all domain exceptions. Thrown anywhere in the service layer;
 * translated to the error envelope by {@link GlobalExceptionHandler}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
