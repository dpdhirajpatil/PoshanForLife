package com.poshanforlife.api.exception;

import com.poshanforlife.api.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every exception into the shared error envelope:
 * { "success": false, "error": "...", "code": "...", "details"?: ... }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Domain exceptions carry their own code and status. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        return respond(ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    /** Bean validation on @RequestBody DTOs → 422 VALIDATION_ERROR with field map. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return respond(ErrorCode.VALIDATION_ERROR, "Validation failed", fields);
    }

    /** Bean validation on @RequestParam/@PathVariable → 422 VALIDATION_ERROR. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> violations = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                violations.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return respond(ErrorCode.VALIDATION_ERROR, "Validation failed", violations);
    }

    /** Malformed JSON / wrong parameter types / missing parameters → 400 VALIDATION_ERROR. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Malformed request"));
    }

    /** Multipart upload over the configured size cap → 422 VALIDATION_ERROR. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return respond(ErrorCode.VALIDATION_ERROR, "File exceeds the maximum upload size", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.AUTH_REQUIRED, "Authentication required", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.INSUFFICIENT_ROLE, "You do not have permission to perform this action", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Method not allowed"));
    }

    /** Last-resort fallback — never leak internals to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", null);
    }

    private ResponseEntity<ApiErrorResponse> respond(ErrorCode code, String message, Object details) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiErrorResponse.of(code, message, details));
    }
}
