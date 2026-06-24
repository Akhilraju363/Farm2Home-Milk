package com.farm2home.farm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FarmException extends RuntimeException {
    public FarmException(String message) { super(message); }
}
