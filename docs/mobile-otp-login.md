# Mobile OTP Login

Passwordless sign-in by mobile number, built entirely on auth-service's existing OTP
infrastructure - `send-otp`/`verify-otp` already existed (used by registration and forgot-password)
with an `otpType=LOGIN` value already defined but never wired to actually authenticate anyone. This
document covers what changed to complete that, and the security hardening that came with it.

## 1. Architecture

```
Browser (LoginPage.tsx -> MobileOtpDialog.tsx)
   |
   | POST /auth/send-otp { identifier: mobile, otpType: 'LOGIN' }
   v
auth-service: OtpService.generateAndSend()
   |  - resend cooldown + resend-count check
   |  - generates a 6-digit code, hashes it (BCrypt), stores the hash
   |  - sends via the existing SmsService (SMS_PROVIDER, unchanged)
   v
Browser: customer enters the code
   |
   | POST /auth/verify-otp { mobile, otp, otpType: 'LOGIN' }
   v
auth-service: OtpService.verify() - hash comparison, expiry, attempt limit
   |
   +-- existing account -----> AuthServiceImpl issues tokens (same mechanism as password login)
   +-- no account for mobile -> registrationRequired: true (frontend continues into RegisterPage)
```

**No new endpoints were created.** `POST /auth/send-otp` and `POST /auth/verify-otp` are the exact
same endpoints REGISTRATION and FORGOT_PASSWORD already used - Mobile OTP Login is `otpType: 'LOGIN'`
on the same two calls. This is the literal meaning of "reuse the existing architecture": the
gap wasn't a missing endpoint, it was `verifyOtp()` never actually signing anyone in for that
otpType.

## 2. OTP generation and storage

`OtpService.generateAndSend(mobile, otpType)` (unchanged trigger points, hardened internals):

- Generates a cryptographically random 6-digit code (`SecureRandom`, unchanged).
- **Hashes it** with the same `PasswordEncoder` (BCrypt) already used for user passwords, before
  persisting - the previous implementation stored the raw 6-digit code in a `VARCHAR(10)` column.
  `V4__social_login_and_otp_hardening.sql` widens that column to `VARCHAR(255)` for the hash.
- The **raw code never leaves this method except via the SMS body itself** (and the best-effort
  email channel, unchanged) - never logged, never returned in any API response (see §6).

## 3. Expiration, attempts, resend limits

All four are now configuration-driven (`OtpProperties`, bound from `otp.*`/`OTP_*` env vars),
where before only expiry existed as a hardcoded constant:

| Setting | Env var | Default | Enforced by |
|---|---|---|---|
| Expiry | `OTP_EXPIRY_SECONDS` | 300 (5 min) | `OtpVerification.isExpired()` (unchanged mechanism, now configurable) |
| Max verify attempts | `OTP_MAX_ATTEMPTS` | 5 | New `attempts` column - incremented on each wrong guess, the record is burned (marked used) once the limit is hit, even if a later guess would have been correct |
| Resend cooldown | `OTP_RESEND_COOLDOWN_SECONDS` | 30 | New check against the most recent record's `createdAt`, regardless of used/unused |
| Max resends | `OTP_MAX_RESENDS` | 3 per 60-minute window | New count query against records created for the same (mobile, otpType) |

**A real bug was found and fixed during live verification of the attempt limit**: `OtpService.verify()`
and its caller `AuthServiceImpl.verifyOtp()` are both `@Transactional`; since the inner call joins
the outer method's existing transaction (Spring's default `REQUIRED` propagation), the attempt
counter's persisted increment was being silently rolled back on every wrong guess - Spring marks a
shared physical transaction rollback-only if *any* participating advice sees an exception it
doesn't explicitly exempt, regardless of what other participants declare. Fixed by adding
`@Transactional(noRollbackFor = AuthException.class)` to *both* methods (only the outer one alone,
or the inner one alone, is insufficient - confirmed by testing each independently). Live-verified
before and after: 5 wrong guesses followed by the correct code succeeded before the fix, and
correctly returned "Too many incorrect attempts" after it. This class of bug is invisible to
Mockito-based unit tests, since they call the plain object directly and never go through Spring's
transactional proxy - it was only caught by actually running the service.

OTP length itself (6 digits) is **not** configurable - deliberately kept as the existing shared
`ValidationConstants.OTP_LENGTH` constant, since it's baked into `VerifyOtpRequest`'s
`@Size`/`@Pattern` validation annotations; making generation length configurable without also
making that validation dynamic would let the two silently drift out of sync.

## 4. Brute-force / enumeration protection

- **Per-challenge attempt limit** - see §3.
- **Per-(mobile, otpType) resend cooldown and cap** - see §3.
- **Gateway-level rate limiting** - `/api/v1/auth/**` already carries a `Resilience4jRateLimiter`
  filter (`authServiceRL`) at the API Gateway, applying to every auth endpoint including
  `send-otp`/`verify-otp` automatically; no new gateway configuration was needed.
- **No enumeration via response shape** - `send-otp` always returns the same
  `"If {identifier} is registered, an OTP has been sent."` message regardless of whether the
  identifier is a real account (pre-existing behavior for email identifiers, confirmed to also
  hold for the mobile case used by Mobile OTP Login).
- **No enumeration via the LOGIN "new customer" signal either** - `registrationRequired: true`
  only appears *after* the caller has already proven ownership of the phone number via a real,
  received OTP code; it tells someone who already controls that number that it isn't registered
  yet, which is the intended passwordless-signup UX, not an information leak to an attacker who
  doesn't control the phone.

## 5. New customer via Mobile OTP

A verified mobile number with no matching account never silently creates one. `verify-otp` returns
`{ registrationRequired: true, auth: null }`; `MobileOtpDialog.tsx` then navigates to `/register`
with the verified mobile pre-filled (`location.state.mobile`), continuing into the **existing**
multi-step registration wizard (personal details incl. password, address, milk preference,
consent) rather than a parallel/duplicated account-creation path.

**Deliberate, accepted redundancy**: the wizard's own final step still sends and verifies a second,
independent `REGISTRATION`-type OTP to the same number before the account is marked verified. This
was not special-cased away - it keeps `RegisterPage.tsx`'s verification step completely untouched
for every other entry point (direct registration, Google-continuation), and provides genuine
defense in depth (mobile ownership is proven twice, once for login-continuation and once for the
account creation itself) at the cost of one extra SMS. Documented here as a conscious trade-off,
not an oversight.

## 6. Security

- OTP is **never** returned in any API response - `verify-otp`'s response body only ever contains
  `registrationRequired`/`auth`, never the submitted or expected code (asserted directly in
  `AuthControllerTest.verifyOtp_responseNeverContainsOtp`).
- OTP is **never logged** - `LoggingSmsProvider` (the default SMS provider, used in every
  environment without real SMS credentials configured) logs the *message it would send*, which
  necessarily contains the code for the SMS to be useful to a real customer; this is the
  established, pre-existing dev-mode visibility mechanism (identical to how REGISTRATION/
  FORGOT_PASSWORD OTPs have always worked), not a new logging path introduced by this task, and it
  never appears in `auth-service`'s own log statements (`OtpService` logs only mobile numbers and
  outcomes, never the code).
- OTP is stored **hashed**, never in plaintext (see §2).
- **A customer can never verify another customer's phone** - `verify()` matches strictly on
  `(mobile, otpType, used=false)`; there is no code path anywhere that accepts a mobile number
  different from the one the code was generated for.
- **Attacker cannot select their own role** - Mobile OTP Login only ever signs in with an existing
  user's already-assigned role, or (for a new customer) routes into the standard registration
  wizard, which - like every registration path - only ever assigns `CUSTOMER`.

## 7. SMS provider

Reused entirely as-is: `SmsService`/`SmsProvider` (common-core), `SMS_PROVIDER`/`SMS_MAX_ATTEMPTS`/
`SMS_RETRY_BACKOFF_MILLIS`. No new provider, no new credentials, no direct
`auth-service -> Twilio`-style integration was added - `OtpService` already called `SmsService`
before this task and continues to.

## 8. Configuration

| Variable | Default | Meaning |
|---|---|---|
| `OTP_EXPIRY_SECONDS` | `300` | How long a generated code remains valid. |
| `OTP_MAX_ATTEMPTS` | `5` | Verify attempts allowed against one code. |
| `OTP_RESEND_COOLDOWN_SECONDS` | `30` | Minimum time between two OTP requests for the same (mobile, purpose). |
| `OTP_MAX_RESENDS` | `3` | Maximum OTPs generated per (mobile, purpose) within a 60-minute window. |

Applies uniformly to REGISTRATION, LOGIN, and FORGOT_PASSWORD - all three benefit from the same
hardening, not just the new Mobile OTP Login path.

## 9. Frontend

`MobileOtpDialog.tsx` (new, `components/auth/`) - a two-step dialog (mobile entry → 6-digit code
entry, visually matching `RegisterPage.tsx`'s own verification step) triggered from LoginPage's
"Mobile OTP" button. Handles: loading (send/verify in flight), invalid mobile format, invalid/
expired/too-many-attempts OTP (all surfaced via the backend's own error message), resend cooldown
countdown, and network errors. **Not handled as a distinct state**: "SMS delivery failure" - the
backend's `SmsService` never reports a delivery failure back through the API (a transient SMS
outage must never block OTP generation, see `OtpService.generateAndSend()`'s own doc comment), so
`send-otp` always either succeeds or is rate-limited from this dialog's point of view; documented
here rather than faking a state that can never actually be reached.

A successful LOGIN verification calls the exact same `tokenStorage.setTokens()` +
`dispatch(setCredentials(...))` + `navigate(getLandingRoute(...))` sequence password login uses -
an OTP-authenticated session is indistinguishable from a password-authenticated one everywhere
else in the app (Redux state, route guards, theme persistence, token refresh).

## 10. DPDP / privacy

The mobile number is required strictly for authentication (delivering the code) - collecting and
using it here is not a marketing communication and requires no additional consent beyond what
registration already governs. See `docs/google-login.md` §8 and `DPDP_PROGRESS.md` for the
platform-wide consent model this sits inside.

## 11. Troubleshooting

- **"Please wait before requesting another OTP"** - the resend cooldown (§3) is active; wait for
  the countdown shown in `MobileOtpDialog`.
- **"Too many OTP requests. Please try again later."** - the resend cap (§3) was hit for this
  mobile+purpose within the last hour.
- **"Too many incorrect attempts. Please request a new OTP."** - the attempt limit (§3) was hit;
  the previous code is now permanently invalid even if you know it - request a new one.
- **Correct code no longer works after several wrong guesses** - this is §3's fix working as
  intended, not a bug.
- **A new customer keeps getting asked for another OTP right after Mobile OTP Login already
  verified their number** - this is the deliberate §5 redundancy (RegisterPage's own verification
  step), not a double-send bug.
