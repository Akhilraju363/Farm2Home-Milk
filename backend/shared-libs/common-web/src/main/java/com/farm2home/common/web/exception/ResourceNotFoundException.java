package com.farm2home.common.web.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message, cause);
    }
}