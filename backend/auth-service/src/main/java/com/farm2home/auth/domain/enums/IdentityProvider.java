package com.farm2home.auth.domain.enums;

/** Only Google is implemented - see AUTH_SOCIAL_OTP_PROGRESS.md's explicit scope decision (no
 *  Facebook/Apple/other providers were asked for or built). Kept as an enum rather than a bare
 *  string on UserIdentity purely for the same reason NotificationChannel is an enum: a fixed,
 *  compile-time-checked set of values, not because more providers are expected imminently. */
public enum IdentityProvider {
    GOOGLE
}
