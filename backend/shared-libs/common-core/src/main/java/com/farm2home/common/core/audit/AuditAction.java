package com.farm2home.common.core.audit;

/**
 * Canonical action codes for AuditLog.action, kept as plain constants (not a JPA enum) so a
 * service can still record an ad-hoc action without a shared-lib change - these just give the
 * common cases one spelling instead of each service inventing its own.
 */
public final class AuditAction {

    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String PAYMENT = "PAYMENT";

    private AuditAction() {
    }
}
