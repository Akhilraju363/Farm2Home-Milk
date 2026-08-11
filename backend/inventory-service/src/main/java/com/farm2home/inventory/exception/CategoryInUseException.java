package com.farm2home.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when deleting a ProductCategory that's still assigned to at least one non-deleted
 *  Product - kept distinct from DuplicateResourceException (also 409, but a different kind of
 *  conflict) so callers/logs can tell the two apart. */
@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException(String message) {
        super(message);
    }
}
