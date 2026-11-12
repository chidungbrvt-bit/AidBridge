package com.aidbridge.shared.exception;

import org.springframework.http.HttpStatus;

/** Only fixed, non-sensitive messages belong in this exception. */
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public BusinessException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static BusinessException missing() {
        return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
    }
    public static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
    }
    public static BusinessException state() {
        return new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE", "Operation is not allowed in the current state");
    }
    public static BusinessException stale() {
        return new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The case has changed; refresh and try again");
    }
    public static BusinessException quantityExceeded() {
        return new BusinessException(HttpStatus.CONFLICT, "QUANTITY_EXCEEDED", "Requested quantity exceeds the remaining capacity");
    }
    public static BusinessException disputeOpen() {
        return new BusinessException(HttpStatus.CONFLICT, "DISPUTE_OPEN", "The case has an open dispute");
    }
}
