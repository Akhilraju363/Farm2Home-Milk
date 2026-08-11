package com.farm2home.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** For "this already exists" cases (e.g. registering a mobile/email that's already taken) - kept
 *  distinct from AuthException (401), since a duplicate-resource conflict isn't an authentication
 *  failure. The frontend's axios interceptor treats every 401 as an expired/invalid session and
 *  force-logs the user out, which previously fired incorrectly on a routine "already registered"
 *  validation error during signup. */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
