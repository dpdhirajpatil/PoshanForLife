package com.poshanforlife.api.exception;

/** PDF text/image extraction or the Claude structured-extraction call failed. */
public class OcrExtractionException extends ApiException {

    public OcrExtractionException(String message) {
        super(ErrorCode.OCR_FAILED, message);
    }

    public OcrExtractionException(String message, Throwable cause) {
        super(ErrorCode.OCR_FAILED, message);
        initCause(cause);
    }
}
