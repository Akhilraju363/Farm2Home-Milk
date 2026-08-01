package com.farm2home.common.core.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative audit hook for the common case: a service method that creates/updates/deletes/
 * uploads a single entity, returning either the entity's response DTO (with a getId()) or void
 * (in which case the id is taken from the first UUID-typed argument). AuditAspect records success
 * automatically after the method returns, and failure (with the exception message) if it throws.
 *
 * Not every operation fits this shape - branching actions (e.g. "add stock" vs "reduce stock"
 * in one method) or cases needing old/new value capture call AuditLogService directly instead.
 * Both paths go through the same AuditLogService, so there's exactly one persistence mechanism.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    /** One of the {@link AuditAction} constants. */
    String action();

    /** Logical entity type, e.g. "Customer", "Product". */
    String entityType();
}
