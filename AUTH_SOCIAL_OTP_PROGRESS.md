# Google Sign-In + Mobile OTP Authentication — Progress Log

Branch: `compliance/dpdp` (not pushed, not committed — per explicit instruction). Farm2Home
already had a working password-login system before this task; both new methods are additive
channels into that same system, not a rebuild.

---

## 1. Initial architecture audit (before any code was written)

- **auth-service** already owns the entire authentication domain: `User` (implements
  `UserDetails`; `username`/`email`/`mobile`/`passwordHash`, `Set<Role>`), `Role`/`RoleType`
  (`SUPER_ADMIN`/`FARM_MANAGER`/`DELIVERY_MANAGER`/`DELIVERY_PARTNER`/`CUSTOMER`, DB-seeded),
  `RefreshToken` (SHA-256-hashed, revocable, single-use), `JwtService` (HMAC-signed access +
  refresh tokens, `jjwt`), `AuthServiceImpl`/`AuthController` (`register`/`login`/`send-otp`/
  `verify-otp`/`refresh-token`/`logout`/`me`), `SecurityConfig` (stateless JWT filter chain, its
  own `PUBLIC_ENDPOINTS` allowlist), BCrypt `PasswordEncoder`.
- **`OtpVerification`/`OtpService`/`OtpType` already existed** - `OtpType.LOGIN` was already
  defined in both the backend enum and the frontend type, and `/send-otp`/`/verify-otp` already
  accepted it - but `verifyOtp()` never did anything with a `LOGIN` type beyond validating the
  code; no tokens were ever issued for it. **Mobile OTP Login was not a green-field feature - it
  was an already-half-built, silently-incomplete one**, matching the task's own framing exactly.
- **The existing OTP storage was plaintext** (`otp_verifications.otp VARCHAR(10)`, no hash) with
  **no attempt limit, no resend cooldown, no resend cap** - `verify()` only checked
  exists/expired/matches. A real, pre-existing security gap directly in scope for this task's own
  explicit "never store OTP in plaintext" instruction, not something introduced fresh.
- **No Google/OAuth/social login code existed anywhere** in the codebase (confirmed by repo-wide
  search) - genuinely new.
- **No Redis anywhere in this stack** (confirmed by repo-wide search) - only Resilience4j's
  in-memory rate limiter, already applied to the entire `/api/v1/auth/**` route at the API Gateway.
- **auth-service had no Google ID-token verification library and no outbound HTTP client** at all
  (self-contained by design - the sole owner of the `User` table). `com.google.api-client:
  google-api-client` (Google's own official library) was added; confirmed reachable and resolved
  online once (this environment has internet access, verified before adding), then cached for
  subsequent offline builds.
- **`users.password_hash NOT NULL` and `users.mobile NOT NULL UNIQUE`** - both hard constraints
  that shape the entire account-linking/creation design below (see §4).
- **`CustomerEventConsumer` (customer-service)** already creates the `customer.customers` row
  asynchronously from `CUSTOMER_CREATED` (Kafka) - reused as-is for every new account, Google-linked
  or not.
- **Frontend**: `LoginPage.tsx` already rendered disabled "Google"/"Mobile OTP" buttons with
  "isn't available yet" tooltips, matching the supplied screenshot exactly - the task was to wire
  real functionality behind buttons that already existed, not design new ones.
  `RegisterPage.tsx`'s multi-step wizard (`PersonalDetailsStep` etc.) already accepted a
  `defaultValues?: Partial<PersonalDetails>` prop - an existing extension point reused for prefill,
  not a new mechanism. `authSlice`/`tokenStorage`/`getLandingRoute`/`ProtectedRoute` were all
  already in place and completely unchanged by this task.
- **API Gateway** has its own, *separate* `JwtAuthenticationFilter` with its own hardcoded
  `PUBLIC_PATHS` allowlist (confirmed a recurring pattern in this codebase from prior session
  work) - any new public auth endpoint needs updating in *two* places, not one.

## 2. Existing authentication flow (documented before changing it)

Password login: `POST /auth/login {identifier, password}` → `AuthenticationManager` (delegates to
`UserDetailsServiceImpl` + BCrypt) → `AuthServiceImpl.buildAuthResponse()` (JWT access + refresh
token pair, refresh token hashed and persisted, previous refresh tokens revoked) → same
`AuthResponse` shape returned by register/refresh. Frontend: `tokenStorage.setTokens()` (localStorage
if "Remember Me", else sessionStorage) → `dispatch(setCredentials())` → `navigate(getLandingRoute())`.

## 3. Google implementation

- **`GoogleTokenValidator`** (new, auth-service): wraps `GoogleIdTokenVerifier` (Google's official
  library) configured with `setAudience(GOOGLE_CLIENT_ID)`. A single `.verify(credential)` call
  checks signature (against Google's rotating public keys, fetched/cached internally), issuer,
  audience, and expiry - all per the library's own documented contract, satisfying the task's
  explicit validation checklist without any hand-rolled JWT/JWKS handling. Fails closed
  (`401`) when `GOOGLE_CLIENT_ID` is blank, never attempting verification against no audience.
- **`AuthServiceImpl.googleAuth()`**: returning-user lookup by `(provider, providerUserId)` first
  (stable, never by email); first-time sign-in with a Google-**verified** email matching an
  existing account auto-links and signs in; first-time sign-in with an **unverified** email match
  is rejected outright (`401`, no link, no token) - this is the one deliberate account-takeover
  guard in the whole design (task Scenario B); no match at all returns
  `registrationRequired: true` with server-validated name/email for the frontend to pre-fill, no
  account created. Full reasoning and the decision table live in `docs/google-login.md` §3.
- See `docs/google-login.md` for the complete architecture, Google Cloud Console setup steps, and
  troubleshooting.

## 4. Mobile OTP implementation

- Reuses the **exact existing** `POST /auth/send-otp` / `POST /auth/verify-otp` endpoints with
  `otpType: 'LOGIN'` - no new endpoints. `verifyOtp()` now returns an `OtpVerifyResponse`
  (`registrationRequired`, `auth`) instead of nothing; for `LOGIN` this is the login itself
  (existing account → tokens; no account → `registrationRequired: true`, continue into
  `RegisterPage`, mobile pre-filled).
- OTP generation/verification hardened: BCrypt-hashed storage (never plaintext), configurable
  expiry/attempt-limit/resend-cooldown/resend-cap (`OtpProperties`), all uniformly applied to
  REGISTRATION/LOGIN/FORGOT_PASSWORD alike.
- See `docs/mobile-otp-login.md` for the complete architecture, including a real bug (§9 below)
  found and fixed during live verification of the attempt limit.

## 5. Database changes

Single new migration, `auth-service/V4__social_login_and_otp_hardening.sql` (next version after
the existing `V1`-`V3`; none of those were edited):

- **`auth.user_identities`** (new table) - `user_id` (FK), `provider`, `provider_user_id`, `email`,
  `email_verified`, `display_name`, timestamps. Unique `(provider, provider_user_id)`. Index on
  `user_id`. A separate table rather than columns on `users` - keeps the existing, heavily-relied-
  -on `User` entity/table completely untouched, and naturally supports "at most one identity per
  provider per user" via the unique constraint alone.
- **`auth.otp_verifications`**: `otp` widened from `VARCHAR(10)` to `VARCHAR(255)` (to hold a
  BCrypt hash instead of 6 raw digits); new `attempts INT NOT NULL DEFAULT 0` column.

No `users` table columns were added or changed - `password_hash NOT NULL` stays enforced (a
Google-only account gets a random, never-disclosed BCrypt hash - functionally password-login-proof
without weakening the schema constraint), `mobile NOT NULL UNIQUE` stays enforced (see §4 - it's
exactly why a brand-new Google/OTP customer is routed into the existing registration wizard rather
than an account being created directly).

## 6. API changes

| Endpoint | Change |
|---|---|
| `POST /auth/google` | **New.** `{credential}` → `{registrationRequired, auth?, firstName?, lastName?, email?}`. Public (added to both auth-service's `SecurityConfig` and the API Gateway's separate `JwtAuthenticationFilter` allowlists). |
| `POST /auth/verify-otp` | Response body changed from empty (`data: null`) to `OtpVerifyResponse {registrationRequired, auth?}` - additive, confirmed backward-compatible (no existing frontend caller read the previous empty body). `LOGIN` otpType now actually authenticates. |
| `POST /auth/register` | `RegisterRequest` gained one optional field, `googleCredential` - re-validated server-side when present, links the resulting account to that Google identity. Every other field/behavior unchanged. |
| `POST /auth/send-otp`, `/login`, `/refresh-token`, `/logout`, `/me` | **Unchanged.** |

No internal service ports exposed; no new endpoint bypasses the gateway's normal routing.

## 7. Frontend changes

Scoped exactly to what the task allowed:

- **`LoginPage.tsx`**: Google button wired to Google Identity Services (`window.google.accounts.id
  .initialize`/`.prompt()`, loaded via a `<script>` tag in `index.html`) - preserves the existing
  custom-styled button rather than rendering Google's own component. Mobile OTP button opens the
  new dialog. Both produce the same `tokenStorage`/`setCredentials`/`getLandingRoute` flow password
  login already used.
- **`components/auth/MobileOtpDialog.tsx`** (new): two-step dialog, mirrors `RegisterPage`'s own
  OTP-entry visual style. See `docs/mobile-otp-login.md` §9 for the full state matrix.
- **`RegisterPage.tsx`**: reads an optional `location.state` prefill (`mobile`/`firstName`/
  `lastName`/`email`/`googleCredential`) into the *existing* `defaultValues` prop of
  `PersonalDetailsStep` - zero new wizard steps, zero duplicated registration logic. Passes
  `googleCredential` through to `authService.register()` when present.
- **`authService.ts`/`auth.types.ts`**: `googleAuth()` added; `verifyOtp()`'s return type updated
  to the new response shape.

No changes to `authSlice.ts`, `tokenStorage.ts`, `ProtectedRoute.tsx`, `roleLanding.ts`,
`axiosClient.ts`, or any consent component - all reused exactly as they were.

## 8. Security decisions

- **Google credential always re-validated server-side** - the frontend never constructs an
  identity from the browser-visible token claims; every field used for matching/creation comes
  from `GoogleTokenValidator`'s own read of the verified payload.
- **Role never accepted from either flow** - `GoogleAuthRequest` has exactly one field
  (`credential`); `GooglePayload` has no role field; Mobile OTP Login's `verify-otp` accepts no
  role field either. An existing user always keeps their existing role; a new customer (either
  path) always gets exactly `CUSTOMER`, via the same `roleRepository.findByNameAndDeletedFalse
  (RoleType.CUSTOMER)` every registration already used.
- **Unverified-email Google match never auto-links** (§3/Scenario B) - the one deliberate
  anti-takeover rejection in the design.
- **OTP never returned in any response, never logged, never stored raw** (§4) - asserted directly
  in a controller test (`verifyOtp_responseNeverContainsOtp`).
- **A customer cannot verify another customer's phone** - `OtpService.verify()` matches strictly
  on `(mobile, otpType, used=false)`; no code path accepts a different mobile than the one a code
  was generated for.
- **No secret of any kind for Google** - only a client ID exists anywhere in this codebase,
  backend or frontend (see `docs/google-login.md` §2 for why this flow needs none).

## 9. A real bug found (and fixed) during live verification

`OtpService.verify()`'s attempt-counter increment was silently discarded by Spring's transaction
rollback semantics on every wrong guess - fully explained in `docs/mobile-otp-login.md` §3. Live-
verified broken (5 wrong guesses followed by the *correct* code still succeeded), root-caused to
transaction propagation between `OtpService.verify()` and its caller `AuthServiceImpl.verifyOtp()`
(both `@Transactional`, same physical transaction), fixed by adding
`@Transactional(noRollbackFor = AuthException.class)` to *both* methods, and re-verified live
(same 5-wrong-guesses-then-correct-code sequence now correctly rejected with "Too many incorrect
attempts"). This class of bug is invisible to Mockito-based unit tests (no Spring transactional
proxy involved when calling the plain object directly) - it was caught specifically *because* live
verification against the real database was performed, not skipped.

## 10. Account-linking decision

Full decision tree and rationale in `docs/google-login.md` §3. Summary: stable lookup by
`(provider, providerUserId)` first; auto-link only when the provider itself vouches for the email
(`email_verified=true`); never auto-link (or create/authenticate anything) on an unverified email
match; no account is ever created directly from a Google identity alone (mobile is mandatory
platform-wide and Google never supplies one) - routed into the existing registration wizard
instead.

## 11. DPDP considerations

Both flows collect only what's strictly necessary for authentication (Google: `sub`/`email`/
`email_verified`/name, read from the validated token only, no access/refresh token requested or
stored; Mobile OTP: the phone number itself, already required for every account). Neither is a
marketing-consent event - unchanged from how password registration already worked. See
`docs/google-login.md` §8 and `docs/mobile-otp-login.md` §10, and the platform-wide model in
`DPDP_PROGRESS.md`.

## 12. Tests

auth-service, full suite: **65/66 pass** (was 27 tests pre-existing; net new: `GoogleAuth` nested
class in `AuthServiceImplTest` (5), `Otp` nested class extended (+4, now 7), `Register` nested
class extended (+3, now 7), `GoogleTokenValidatorTest` (5, entirely network-independent - see its
own doc comment), `OtpServiceTest` rewritten for hashed storage/cooldown/resend-cap/attempt-limit
(11 tests, up from 7), `AuthControllerTest` extended with a new `Google` nested class (5) and 4
additional `verify-otp` scenarios). The **one** non-passing test is the same pre-existing,
permanent, Docker/Testcontainers-gated `AuthServiceApplicationTests` every service in this
environment has - zero real regressions, zero real failures.

Explicit coverage of the task's own security-test list: valid/invalid/expired Google token paths
(mocked `GooglePayload`, since a real Google signature check needs live network - see §13),
duplicate Google identity, existing-email linking (verified and unverified), unauthorized-role-
assignment attempts (Google request has no role field at all, asserted by reflection); valid/
invalid/expired/reused/too-many-attempts/wrong-challenge OTP, OTP never in response, OTP hashed
storage (asserted via BCrypt prefix and non-digit-match on the persisted value); CUSTOMER-cannot-
become-admin and existing-role-preserved (both flows).

## 13. Live verification

**LIVE VERIFIED** (real auth-service process, restarted twice during this task, against the real
local Postgres; not through the API Gateway, which runs on a port already occupied by an unrelated
system service in this dev environment - documented as an environment fact, not worked around):

1. `V4` migration applied cleanly to the real schema (`"Successfully applied 1 migration to schema
   auth, now at version v4"`).
2. Full password registration + login - zero regression, tokens issued correctly.
3. Mobile OTP Login, existing account: `send-otp` → OTP read from the real SMS-provider log line →
   `verify-otp` → real tokens issued, real `refresh_tokens` row persisted.
4. OTP reuse correctly rejected (`"No OTP found"` once used).
5. Resend cooldown correctly rejected an immediate second `send-otp`.
6. **Max-attempts limit**: found broken live (§9), fixed, **re-verified live** - 5 wrong guesses
   then the correct code now correctly returns `"Too many incorrect attempts"`.
7. Mobile OTP Login, no existing account: `verify-otp` correctly returned
   `{registrationRequired: true, auth: null}` without creating any account.
8. `POST /auth/google` with `GOOGLE_CLIENT_ID` unset in this environment correctly failed closed
   (`401 "Google Sign-In is not configured on this server"`) rather than accepting an unverifiable
   credential - proves the fail-closed path genuinely works, not just in a mock.
9. All test data (1 `auth.users` row, its `refresh_tokens`/`otp_verifications` rows) created during
   this verification was deleted afterward and confirmed zero via a direct database query.

**NOT VERIFIED**: a real, successful Google Sign-In against Google's actual servers. No
`GOOGLE_CLIENT_ID`/`VITE_GOOGLE_CLIENT_ID` is configured in this dev environment (no Google Cloud
Console project was created for this task), so the token-signature-verification path, the browser-
side `google.accounts.id` prompt flow, and the full linking/registration-continuation UI were
exercised only via mocked unit tests, never against a live Google credential end-to-end. Reported
here exactly as the task instructed: **"Provider integration verified through mocked tests; real
provider E2E not executed."**

**BLOCKED**: nothing else was blocked - Postgres was reachable throughout; Kafka was not running in
this environment (a pre-existing, permanent fact of this dev setup established in earlier work),
so `CUSTOMER_CREATED` publication during registration could not be observed being consumed
live in this task's testing (customer-service's own consumption of that event was already verified
in earlier, unrelated session work, and is unchanged by this task).

## 14. Known limitations

- **Real Google OAuth E2E** requires a configured Google Cloud Console project and real
  `GOOGLE_CLIENT_ID`/`VITE_GOOGLE_CLIENT_ID` values - not available in this dev environment (§13).
- **No "link Google to my existing account" settings flow** - an unverified-email collision
  (§3/§10) tells the customer to log in with their password instead; a self-service linking screen
  from an authenticated context would be a natural follow-up but wasn't asked for and is out of
  scope for this pass.
- **No unlink-identity endpoint.**
- **The `attempts`/resend-cooldown transaction-safety fix (§9) has no automated regression test** -
  Mockito unit tests cannot exercise real Spring transactional-proxy rollback semantics, and adding
  a `@DataJpaTest`/`@SpringBootTest` integration test would need Testcontainers, which needs
  Docker - unavailable in this environment (the same permanent constraint noted throughout this
  session's work). The fix is real and was live-verified twice (broken, then fixed); it simply
  isn't guarded against a future accidental regression by CI in this repo today.
- **Mobile OTP Login's "new customer" path re-verifies the mobile a second time** in the
  registration wizard's own step - a deliberate, documented trade-off (§4/`docs/mobile-otp-
  login.md` §5), not a bug.
- **No API Gateway live restart was performed** - it runs on a port occupied by an unrelated
  system process in this dev environment (documented, not worked around); its own
  `JwtAuthenticationFilter` change was verified by compilation only, mirroring the exact same
  allowlist entry already proven correct in auth-service's own `SecurityConfig`.

## 15. Configuration required before production

| Variable | Required for |
|---|---|
| `GOOGLE_CLIENT_ID` (backend) + `VITE_GOOGLE_CLIENT_ID` (frontend, same value) | Google Sign-In to function at all - see `docs/google-login.md` §6 for Cloud Console setup. |
| `SMS_PROVIDER` set to a real provider (currently `logging` everywhere) | Mobile OTP Login to actually deliver SMS - pre-existing gap, not introduced by this task. |
| `OTP_EXPIRY_SECONDS`/`OTP_MAX_ATTEMPTS`/`OTP_RESEND_COOLDOWN_SECONDS`/`OTP_MAX_RESENDS` | Sensible defaults ship and were live-tested; a production deployment should review them against real fraud/abuse patterns, not treat them as final. |

No lawyer/security review items beyond what `DPDP_PROGRESS.md` already tracks platform-wide - this
task did not change the consent model, only reused it (§11).
