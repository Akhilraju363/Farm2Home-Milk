package com.farm2home.common.web.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends ApiException {

    public BusinessException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_ERROR", message);
    }

    public BusinessException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_ERROR", message, cause);
    }
}