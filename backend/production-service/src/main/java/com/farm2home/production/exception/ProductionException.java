package com.farm2home.production.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ProductionException extends RuntimeException {
    public ProductionException(String message) {
        super(message);
    }
}
