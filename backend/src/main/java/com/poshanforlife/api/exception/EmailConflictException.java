package com.poshanforlife.api.exception;

public class EmailConflictException extends ApiException {

    public EmailConflictException(String email) {
        super(ErrorCode.EMAIL_CONFLICT, "An account with email '" + email + "' already exists");
    }
}
