package com.poshanforlife.api.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes reused verbatim from the original Next.js API contract.
 * The enum name is the wire value of the {@code code} field.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    INSUFFICIENT_ROLE(HttpStatus.FORBIDDEN),
    /** 422 for semantic validation failures; malformed requests map to 400. */
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_CONFLICT(HttpStatus.CONFLICT),
    /** Doctor–patient pair already assigned (assignments feature). */
    ASSIGNMENT_CONFLICT(HttpStatus.CONFLICT),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    OCR_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    /** Fallback for unexpected server errors; not part of the original list. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;
}
