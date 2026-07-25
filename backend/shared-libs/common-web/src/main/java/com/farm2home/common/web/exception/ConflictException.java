package com.farm2home.common.web.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public ConflictException(String message, Throwable cause) {
        super(HttpStatus.CONFLICT, "CONFLICT", message, cause);
    }
}