package com.farm2home.auth.service;

/** The subset of a validated Google ID token's claims this codebase actually needs - see
 *  GoogleTokenValidator. subject ("sub") is Google's stable, permanent identifier for the
 *  account; email/emailVerified/firstName/lastName are read only for account
 *  linking/pre-filling, never trusted as an identity key on their own (see
 *  AUTH_SOCIAL_OTP_PROGRESS.md's account-linking section). */
public record GooglePayload(
        String subject,
        String email,
        boolean emailVerified,
        String firstName,
        String lastName
) {
}
