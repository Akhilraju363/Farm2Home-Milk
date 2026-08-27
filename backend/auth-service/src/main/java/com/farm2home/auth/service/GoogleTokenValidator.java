package com.farm2home.auth.service;

import com.farm2home.auth.config.GoogleAuthProperties;
import com.farm2home.auth.exception.AuthException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Validates a Google Identity Services credential (ID token) server-side using Google's own
 * official client library - never trusts the browser-supplied email/name/subject without this.
 *
 * {@link GoogleIdTokenVerifier#verify} itself checks, per Google's documented contract: the
 * token's signature (against Google's public keys, fetched over HTTPS from
 * {@code www.googleapis.com/oauth2/v3/certs} and cached/rotated internally by the library - no
 * manual JWKS handling here), the issuer (must be {@code accounts.google.com} or
 * {@code https://accounts.google.com}), the audience (must match {@link
 * GoogleAuthProperties#getClientId()} exactly, set via {@code setAudience} below), and expiry.
 * This class only reads claims and enforces the one policy decision not already covered by
 * {@code verify()} itself: an empty/unset client ID (Google Sign-In not configured in this
 * environment) always fails closed, never silently accepts an unverifiable token.
 *
 * No Google client secret exists anywhere in this codebase - ID-token verification (as opposed
 * to an authorization-code exchange) never needs one; only the public client ID is required, and
 * it is safe to also ship to the frontend (see VITE_GOOGLE_CLIENT_ID).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleTokenValidator {

    private final GoogleAuthProperties properties;

    private volatile GoogleIdTokenVerifier verifier;

    public GooglePayload validate(String credential) {
        if (properties.getClientId() == null || properties.getClientId().isBlank()) {
            throw new AuthException("Google Sign-In is not configured on this server.");
        }
        if (credential == null || credential.isBlank()) {
            throw new AuthException("Missing Google credential.");
        }

        GoogleIdToken idToken;
        try {
            idToken = getVerifier().verify(credential);
        } catch (Exception ex) {
            // Malformed token, network failure reaching Google's key endpoint, etc. - never leak
            // the underlying exception detail (could reveal internals of the verification
            // process); the caller only needs to know the credential could not be accepted.
            log.warn("Google credential verification failed: {}", ex.getClass().getSimpleName());
            throw new AuthException("Google sign-in failed. Please try again.");
        }

        if (idToken == null) {
            // verify() returns null (rather than throwing) for a signature/issuer/audience/
            // expiry failure - see GoogleIdTokenVerifier#verify's own contract.
            throw new AuthException("Invalid or expired Google credential.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String subject = payload.getSubject();
        if (subject == null || subject.isBlank()) {
            // Not expected in practice (Google always sets "sub"), but never proceed with an
            // identity that has no stable key to store/match against.
            throw new AuthException("Google credential is missing a subject.");
        }

        String givenName = asString(payload.get("given_name"));
        String familyName = asString(payload.get("family_name"));
        return new GooglePayload(
                subject,
                payload.getEmail(),
                Boolean.TRUE.equals(payload.getEmailVerified()),
                givenName,
                familyName
        );
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    /** Built lazily (not a @Bean) so an unconfigured/blank client ID never fails application
     *  startup - only the first actual Google Sign-In attempt does, via the check in validate()
     *  above. Double-checked locking since GoogleIdTokenVerifier's internal key cache is
     *  reused across every request once built. */
    private GoogleIdTokenVerifier getVerifier() {
        GoogleIdTokenVerifier result = verifier;
        if (result == null) {
            synchronized (this) {
                result = verifier;
                if (result == null) {
                    result = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                            .setAudience(Collections.singletonList(properties.getClientId()))
                            .build();
                    verifier = result;
                }
            }
        }
        return result;
    }
}
