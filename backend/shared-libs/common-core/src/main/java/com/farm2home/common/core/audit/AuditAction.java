package com.farm2home.common.core.audit;

/**
 * Canonical action codes for AuditLog.action, kept as plain constants (not a JPA enum) so a
 * service can still record an ad-hoc action without a shared-lib change - these just give the
 * common cases one spelling instead of each service inventing its own.
 */
public final class AuditAction {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String UPLOAD = "UPLOAD";
    public static final String DOWNLOAD = "DOWNLOAD";
    public static final String ASSIGN = "ASSIGN";
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String PAYMENT = "PAYMENT";
    public static final String DELIVERY = "DELIVERY";
    public static final String EMAIL_SENT = "EMAIL_SENT";
    public static final String OTP_SENT = "OTP_SENT";
    public static final String SMS_SENT = "SMS_SENT";
    public static final String SMS_FAILED = "SMS_FAILED";
    public static final String PUSH_SENT = "PUSH_SENT";
    public static final String PUSH_FAILED = "PUSH_FAILED";

    private AuditAction() {
    }
}
