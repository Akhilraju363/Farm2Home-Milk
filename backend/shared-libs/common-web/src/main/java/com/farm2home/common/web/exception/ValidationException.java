package com.farm2home.common.web.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public ValidationException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, cause);
    }
}