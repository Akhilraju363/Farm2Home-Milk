package com.farm2home.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** For genuine "this already exists" conflicts (e.g. a category name that's already taken) -
 *  kept distinct from InventoryException (400, general validation failures) since a duplicate
 *  is a 409 CONFLICT, not a malformed request. */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
