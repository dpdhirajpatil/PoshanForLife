package com.poshanforlife.api.exception;

public class OcrFailedException extends ApiException {

    public OcrFailedException(String message) {
        super(ErrorCode.OCR_FAILED, message);
    }

    public OcrFailedException(String message, Object details) {
        super(ErrorCode.OCR_FAILED, message, details);
    }
}
