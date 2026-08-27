# Google Sign-In

Google Sign-In is a channel of the existing auth-service authentication system, not a separate
identity service. This document covers architecture, configuration, and account-linking policy.

## 1. Architecture

```
Browser (LoginPage.tsx)
   |
   | Google Identity Services (window.google.accounts.id)
   v
Google's own servers
   |
   | signed ID token ("credential")
   v
POST /api/v1/auth/google  { credential }
   |
   v
auth-service: GoogleTokenValidator.validate(credential)
   |  - verifies signature (Google's rotating public keys), issuer, audience, expiry
   |  - via GoogleIdTokenVerifier, Google's own official client library
   v
AuthServiceImpl.googleAuth()
   |
   +-- known Google identity (user_identities) ---------> sign in (existing User)
   +-- unknown identity, Google-verified email matches --> link + sign in (existing User)
   +-- unknown identity, unverified email matches -------> REJECTED (never auto-links)
   +-- no match at all -----------------------------------> registrationRequired=true
                                                              (frontend continues into RegisterPage)
```

The frontend never constructs or trusts an identity itself - it only ever receives Google's signed
credential and hands it to the backend. Every field used for account matching/creation
(`sub`, `email`, `email_verified`, `given_name`, `family_name`) is read from the **validated**
token payload, never from anything the browser could set independently.

## 2. Backend validation

`GoogleTokenValidator` (auth-service) uses `com.google.api-client:google-api-client` -
Google's own official library - via `GoogleIdTokenVerifier`, configured with
`setAudience(GOOGLE_CLIENT_ID)`. A single call to `.verify(credential)` checks, per the library's
documented contract:

- **Signature** - against Google's public keys, fetched from `www.googleapis.com/oauth2/v3/certs`
  and cached/rotated internally by the library (no manual JWKS handling in this codebase).
- **Issuer** - must be `accounts.google.com` or `https://accounts.google.com`.
- **Audience** - must exactly match the configured `GOOGLE_CLIENT_ID`.
- **Expiry** - rejected if expired.

`GoogleTokenValidator` adds exactly one policy on top: an unconfigured server (blank
`GOOGLE_CLIENT_ID`) always fails closed (`401 "Google Sign-In is not configured on this server"`)
rather than attempting to verify against no audience.

**No Google client secret exists anywhere in this codebase.** ID-token verification (what this
flow does) never needs one - only an authorization-code exchange would, and this app doesn't use
that flow. Only the public client ID is configured, backend and frontend alike.

## 3. Account linking policy

Read the User schema first (see AUTH_SOCIAL_OTP_PROGRESS.md's audit) - `auth.users` already has
`password_hash NOT NULL` and `mobile NOT NULL UNIQUE`. A new table, `auth.user_identities`, links
a Google identity to an existing `User` row rather than adding Google-specific columns to `users`
itself:

| Column | Purpose |
|---|---|
| `user_id` | FK to `auth.users` |
| `provider` | `GOOGLE` (kept as an enum for a hypothetical future provider - none is built) |
| `provider_user_id` | Google's `sub` claim - the permanent, stable identity key |
| `email` / `email_verified` | Snapshotted from the token at link time, for reference only |
| `display_name` | For reference only |

`(provider, provider_user_id)` is unique - `sub` is never the email, precisely so a changed email
on the Google side can never orphan or duplicate the link.

**Decision tree** (`AuthServiceImpl.googleAuth()`):

1. **Returning Google user** - `(GOOGLE, sub)` already links to a `User` → sign in. Stable, direct
   lookup; email is never consulted once a user has linked before.
2. **First-time Google sign-in, Google-verified email matches an existing account** (Scenario A in
   the task's own test matrix) → auto-link the identity to that account, sign in. Safe specifically
   *because* Google itself vouches for the email (`email_verified=true` in the token) - this is the
   standard "provider proves it, so we trust it" pattern used by most real-world identity systems.
3. **First-time Google sign-in, UNVERIFIED email matches an existing account** (Scenario B) →
   **rejected outright**, `401 "An account with this email already exists. Please log in with your
   password."` No account is touched, no identity is linked, no token is issued. This is the one
   deliberate "fail closed" branch protecting against account takeover: Google itself is not
   vouching that the presenter actually owns that email, so this codebase never treats the address
   match as sufficient proof either.
4. **No match at all** → `registrationRequired: true` returned, with `firstName`/`lastName`/`email`
   pre-filled from the validated token (never re-verified beyond that point - just a form
   convenience). No account is created here.

### Why no account is created directly on first Google sign-in

Every Farm2Home account requires a mobile number (`NOT NULL UNIQUE`, used for SMS OTP delivery and
delivery notifications platform-wide) and Google Identity Services' standard `openid email profile`
scopes never supply one. Rather than weakening that constraint, a brand-new Google sign-in is
routed into the **existing registration wizard** (`RegisterPage.tsx`), pre-filled with the
validated name/email, where the customer supplies a mobile number (still independently OTP-verified
via the existing REGISTRATION flow - see `docs/mobile-otp-login.md`), address, and consent, exactly
like any other new customer. `RegisterRequest` gained one optional field, `googleCredential`: when
present, `AuthServiceImpl.register()` re-validates the same credential server-side (never trusts
that a prior `/auth/google` call actually produced it) and links the resulting account
automatically. This reuses 100% of the existing registration architecture - no parallel
account-creation path, no duplicated validation logic.

### Explicitly NOT built (documented, not an oversight)

- **A "link Google to my existing account" authenticated settings flow.** Scenario 3 above (an
  unverified-email collision) tells the customer to log in with their password instead; from there,
  linking would be a natural follow-up feature, but building it wasn't asked for and is out of
  scope for this pass - see AUTH_SOCIAL_OTP_PROGRESS.md's Known Limitations.
- **Unlinking a Google identity.** No delete/unlink endpoint exists.

## 4. Role handling

`AuthServiceImpl.googleAuth()` **never reads or accepts a role from anywhere in the Google flow** -
`GoogleAuthRequest` has exactly one field (`credential`); `GooglePayload` (the validated token's
readable claims) has no role field at all. An existing user signs in with whatever role they
already had in the database, completely unrelated to Google. A new customer created via
`register()` (Google-linked or not) always gets exactly the `CUSTOMER` role, the same
`roleRepository.findByNameAndDeletedFalse(RoleType.CUSTOMER)` lookup every other registration uses
- there is no code path anywhere that lets a Google sign-in produce `FARM_MANAGER`,
`DELIVERY_MANAGER`, `DELIVERY_PARTNER`, or `SUPER_ADMIN`.

## 5. Configuration

| Variable | Where | Purpose |
|---|---|---|
| `GOOGLE_CLIENT_ID` | Backend (auth-service) | Verifies the token's audience. Blank disables Google Sign-In (fails closed). |
| `VITE_GOOGLE_CLIENT_ID` | Frontend build | Same value - safe to ship to the browser (see §2, no secret ever needed). Blank keeps the Google button disabled with an explanatory tooltip. |

Both must be set to the **same** OAuth 2.0 Client ID.

## 6. Google Cloud Console setup (for a real deployment/local testing with a real account)

1. Go to [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials.
2. Create Credentials → OAuth client ID → Application type: **Web application**.
3. Under **Authorized JavaScript origins**, add every origin the frontend is served from (e.g.
   `http://localhost:5173` for local Vite dev, and your production domain). Google Identity
   Services' `prompt()`/One Tap flow validates the calling origin against this list.
4. No redirect URI is needed for this flow (ID-token verification doesn't redirect).
5. Copy the generated **Client ID** (not the secret - this flow doesn't use one) into
   `GOOGLE_CLIENT_ID` and `VITE_GOOGLE_CLIENT_ID`.
6. Restart auth-service and rebuild the frontend for the new values to take effect.

## 7. Frontend integration

`LoginPage.tsx` loads Google Identity Services via a `<script>` tag in `index.html` (not injected
dynamically), then, on clicking the "Google" button:

```js
window.google.accounts.id.initialize({ client_id, callback: handleGoogleCredentialResponse })
window.google.accounts.id.prompt((notification) => { /* handle cancellation */ })
```

This preserves the existing custom-styled button (matching the rest of the login form) rather than
rendering Google's own button component. `prompt()`'s callback only ever fires for
cancellation/unavailability (`isNotDisplayed()`/`isSkippedMoment()`) - a successful sign-in
delivers the credential exclusively through the `callback` passed to `initialize()`.

**Known characteristic of this integration shape (not a bug):** Google Identity Services' One Tap
prompt has its own dismissal cooldown - if a user recently dismissed the prompt, a subsequent
`prompt()` call may not display anything for a period, surfacing as the "cancelled or unavailable"
error message. This is standard GIS behavior across every site that uses it this way, not specific
to this implementation.

## 8. DPDP / privacy

Only `sub`, `email`, `email_verified`, `given_name`, `family_name` are ever read from the token -
no broader profile data, no Google access/refresh token is requested or stored (this flow only
ever handles the one-time ID token). See AUTH_SOCIAL_OTP_PROGRESS.md's DPDP section for how this
fits the platform's existing consent model - in short, account creation/authentication itself is
not a marketing consent event, and nothing here changes that.

## 9. Troubleshooting

- **"Google Sign-In is not configured on this server"** - `GOOGLE_CLIENT_ID` is blank on the
  backend. Set it and restart auth-service.
- **Google button stays disabled with a tooltip** - `VITE_GOOGLE_CLIENT_ID` was blank at frontend
  build time. Set it and rebuild.
- **"Google sign-in was cancelled or is unavailable in this browser"** - either the user genuinely
  dismissed the prompt, or GIS's own cooldown is active (see §7), or the current origin isn't in
  the OAuth client's Authorized JavaScript origins list (check the browser console for a GIS-level
  error in that case).
- **"An account with this email already exists. Please log in with your password."** - the
  Google-verified email matches an existing account, but the token's `email_verified` claim was
  false (rare - typically only for non-Gmail, non-Google-Workspace addresses Google itself hasn't
  independently confirmed). This is the deliberate anti-takeover rejection in §3 step 3, not a bug.
