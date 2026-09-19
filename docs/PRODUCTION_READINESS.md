# Farm2Home — Production Readiness Report

Branch: `spike/render-single-jvm`. Third pass, building on `docs/FREE_DEPLOYMENT_ARCHITECTURE.md`
and `docs/ENVIRONMENT_VARIABLES.md`. Pass 2 fixed the refresh-token collision bug and found a
second, separate security gap (refresh tokens authenticating auth-service's own `/me` as if they
were access tokens). **This pass (3) fixes that gap.** **Nothing was committed, pushed, or opened
as a PR.** auth-service remains a separate standalone service — not aggregated into `backend/app`.

---

## 1. Architecture

Unchanged from `docs/FREE_DEPLOYMENT_ARCHITECTURE.md` §1: Vercel (React SPA) → two standalone
Render Free services (auth-service, `backend/app`'s 12-service aggregate) → one Neon Postgres. No
runtime gateway. See that document for the full diagram and component breakdown.

## 2. Auth deployment model

auth-service, standalone Spring Boot service on Render Free (512 MB / 0.1 vCPU), its own JWT
signing, its own BCrypt(12) password hashing, its own Flyway-managed `auth` schema. Unchanged
architecturally. This pass touched `config/JwtAuthenticationFilter` (token-type distinction, see
§13.2) and `config/SecurityConfig` (a new `AuthenticationEntryPoint` for consistent 401s) —
security-decision logic only, no signing, hashing, or persistence code changed.

## 3. Backend deployment model

`backend/app`, the 12-service single-JVM aggregate (farm, production, customer, inventory,
subscription, order, payment, delivery, notification, invoice, dashboard, reports) on its own
Render Free instance. Unchanged in this pass — no code in `backend/app` was modified.

## 4. Vercel routing

Unchanged: two axios clients (`axiosClient` → `VITE_BACKEND_API_URL`, `authAxiosClient` →
`VITE_AUTH_API_URL`), no Vercel rewrites, both fall back to the local relative `/api/v1` path when
unset. See `docs/FREE_DEPLOYMENT_ARCHITECTURE.md` §8 for the full rationale. **New this pass**:
`authAxiosClient` now uses a 20s timeout instead of the shared 10s default — see §10.

## 5. JWT contract

Signing algorithm (HS256), secret sharing, claim names (`sub`, `userId`, `roles`, `type`), and
expiries (24h access / 7d refresh) are unchanged. **What changed**: refresh tokens now also carry
a `jti` (RFC 7519 JWT ID) — see §13. Access tokens are unaffected; they do not need `jti` (never
persisted/hashed for lookup).

Live-reverified after the fix (real auth-service container → real JWT → real `backend/app`
instance, same `JWT_SECRET`):

| Scenario | Result |
|---|---|
| Valid access token → CUSTOMER-scoped endpoint | **200** |
| Valid access token → SUPER_ADMIN-only endpoint | **403** |
| No token | **401** (was 403 before this pass — see §13.2) |
| Refresh token presented as bearer access token, against `backend/app` | **401** |
| Refresh token presented as bearer access token, against auth-service's own `/me` | **401 — FIXED this pass, see §13.2** |
| Refresh token presented as bearer access token, against auth-service's own `/logout` | **401** |
| Access token presented to `/refresh-token` | **401** (never persisted in `auth.refresh_tokens`, so the hash lookup simply misses) |
| A token with its `type` claim tampered to `REFRESH` without re-signing | **401** (signature no longer verifies — tampering `type` necessarily invalidates it) |
| A token with an unrecognized/garbage `type` value | **not authenticated** (explicit positive match on `ACCESS` required, not "anything but REFRESH") |
| Expired access token | **401** |
| Expired refresh token | **401 by the filter** (rejected during parsing/expiry check), separately **401 by `/refresh-token`** (rejected + revoked by `AuthServiceImpl.refreshToken`, pre-existing, unchanged) |
| Malformed token | **401** |
| Wrong-signature token | **401** |

## 6. CORS

Unchanged from the prior pass; re-verified live this pass against fresh local instances:

| | Allowed origin | Disallowed origin |
|---|---|---|
| auth-service `OPTIONS /api/v1/auth/login` | 200, origin echoed, credentials allowed | 403 |
| `backend/app` `OPTIONS /api/v1/cart` | 200, origin echoed, credentials allowed, `Content-Disposition` exposed | 403 |

No wildcard origin is used with `allowCredentials=true` on either service.

## 7. Database

One Neon Postgres. auth-service owns the `auth` schema (4 migrations, unchanged — the `jti` fix
required **no schema change**, since `jti` is only ever embedded in the JWT payload, never stored
as its own column; only the already-existing `token_hash` column is affected, and only in that its
values are now guaranteed unique going forward). `backend/app` owns its 10 schemas, unchanged.

## 8. Connection pool limits

**Verified this pass against Neon's current official documentation**
(neon.com/docs/connect/connection-pooling): the free tier's default compute (0.25 CU) sets
Postgres `max_connections=104` (97 usable on the direct endpoint after 7 reserved for the Neon
superuser); the pooled (`-pooler`, PgBouncer) endpoint's effective limit is
`0.9 × max_connections` ≈ **94 concurrent connections**, with PgBouncer itself multiplexing up to
10,000 client-facing connections onto that pool.

| Service | Recommended `DB_POOL_MAX_SIZE` | Rationale |
|---|---|---|
| `backend/app` | 6–8 | 12 services already share one pool (Group 1-6 design). |
| auth-service | 4–5 (already the `render` profile default) | Low-QPS (login/register/refresh/OTP only). |
| **Worst-case total** | **~10–13** | **~11–14% of the pooled budget, ~13% of the direct budget** — verified safe with wide headroom. |

No pool sizes were changed this pass; the prior pass's recommendation is now confirmed correct
against current external documentation rather than merely "chosen conservatively."

## 9. Memory

auth-service re-verified again this pass (pass 3, token-type-distinction fix in place), in a real
`--memory=512m --memory-swap=512m` Docker container:

| Point | This pass (3) | Previous pass (2, collision fix only) |
|---|---|---|
| Startup | 302.2 MiB (59.0%) | 308 MiB (60.2%) |
| Peak (full register/refresh-rotation/logout regression, incl. the new token-type checks) | 320.5 MiB (62.6%) | 327.8 MiB (64.0%) |
| OOMKilled | **false** | false |

Startup remains close to the pass-2 baseline (within normal run-to-run variance, if anything
slightly lower) — the fix added a claim-extraction call and a conditional, not new dependencies,
beans, or state. `backend/app` was not touched this pass; its pass-2 numbers (359.4 MiB startup /
397.4 MiB peak after protected/dashboard/reports/invoice/CSV-export traffic) and Group 6's
exhaustive large-export figures (`GROUP6_REPORT.md`) stand unchanged.

## 10. CPU / BCrypt limitation

**Unchanged, not fixed (per explicit instruction not to touch BCrypt cost).** Measured in the
prior pass under Render's realistic `--cpus=0.1` constraint: cold start ~6m23s, first BCrypt(12)
request ~30.1s, warm-JVM steady request ~8.2s.

**Mitigation implemented this pass (client-side only, does not touch the server)**:
`authAxiosClient`'s timeout raised from the shared 10s default to **20s**, using the existing
per-backend axios-client split (no other API call's timeout changed — `axiosClient`, used by every
other service file, is still 10s). 20s comfortably covers the measured 8.2s warm-request case with
real margin. **This does not cover the ~30s first-request-after-cold-start case, nor the ~6-minute
full cold-start case** — those need a different kind of mitigation (a loading-state UX that
explains the wait, a scheduled keep-alive ping to prevent the Render instance from spinning down,
or accepting the risk as a known free-tier limitation) and were deliberately left as an open,
undecided item rather than solved by an even-larger timeout, which would just make a stuck request
look "fine" for a very long time without actually helping the user. **Raising a client timeout
never makes the server respond faster — it only changes how long the browser waits before giving
up.**

## 11. Kafka behavior

Unchanged. Both services' producers stay lazy/non-fatal with no broker; no consumer blocks
startup. Not touched this pass.

## 12. External integrations

Unchanged. Google Sign-In fails closed with no client ID and does not block startup either way;
SMS uses the `logging` provider; Razorpay defaults to `mock`. Not touched this pass.

## 13. Refresh-token security

### 13.1 The collision bug — FIXED

**Root cause** (traced per Step 1's full flow before any change): `JwtService.generateRefreshToken`
built the JWT from `subject` (mobile), `userId`, `type=REFRESH`, `iat`, `exp` only. jjwt encodes
`iat`/`exp` as integer-second `NumericDate` claims. Two refresh tokens minted for the same user
within the same wall-clock second (already-common: register-then-refresh, or two nearly-simultaneous
requests) therefore had **every claim identical**, making the compact JWT string — and therefore
its `AuthServiceImpl.hashToken()` SHA-256 hash, which is the only thing `auth.refresh_tokens`
stores — collide too. `RefreshTokenRepository.findByTokenHashAndRevokedFalse` then matched
whichever row still had `revoked=false`, regardless of which token the caller actually presented,
silently breaking the single-use/revocation guarantee.

**Fix**: `JwtService.generateRefreshToken` now calls jjwt's `.id(UUID.randomUUID().toString())`,
stamping a cryptographically random RFC 7519 `jti` claim on every refresh token. Since `jti` is
part of the signed payload, every issuance now produces a distinct compact string — and therefore
a distinct hash — regardless of `iat`/`exp` truncation, including issuances within the same
millisecond. **Not added to access tokens** (no uniqueness requirement — never persisted/hashed).
**No other claim, the signing algorithm, the secret, expiration handling, revocation logic, or
token hashing were touched.**

**Verification, both automated and live**:

- **Regression test suite** (10 new tests, 2 modified) added this pass:
  `JwtServiceTest$RefreshTokenCollisionRegression` (sequential + 20-way concurrent token
  generation, asserting token/jti/hash uniqueness), `JwtServiceTest$GenerateRefreshToken` (extended
  to assert `jti` is present, non-blank, a valid UUID, and contains no sensitive
  user data), `RefreshTokenServiceLayerRegressionTest` (new file: independently-tracked persistence
  via a real `JwtService` wired into `AuthServiceImpl`, full A→revoked/C→valid rotation, and the
  refresh-endpoint acceptance/rejection matrix).
- **Proved the tests are meaningful, not just green by construction**: temporarily reverted only
  `JwtService.java` to its pre-fix content (via `git stash`) and reran the same test classes — 5 of
  the new/modified tests failed (`sequentialTokensNeverCollide`, `concurrentTokensNeverCollide`,
  `hasRefreshType`, `twoIssuancesPersistTwoDistinctRows`, `fullRotationCycle`), all with the
  fixed implementation passing cleanly. Fix restored before continuing.
- **Live-reproduced against a real Postgres instance, both before and after**: before the fix, two
  sequential refresh calls with the original token produced two DB rows sharing one `token_hash`
  (one `revoked=true`, one `revoked=false`), and reusing the "already-used" original token
  returned **200**. After the fix, the identical sequence produces exactly one row for the original
  hash (correctly `revoked=true`), and reusing it returns **401**.
- **Real 512 MB Docker container, post-fix**: register → refresh (success) → reuse the same
  original token (401, correctly rejected) → use the newly-rotated token (200, valid) → logout
  (200). No OOM.

### 13.2 The refresh-token-as-bearer-access-token gap — FIXED (this pass)

**Root cause** (traced per this pass's Step 1, before any change): auth-service's own bearer-auth
filter (`config/JwtAuthenticationFilter`) never checked the JWT `type` claim — `isTokenValid`
(`JwtService`) only ever compared subject-match and expiry. Both access and refresh tokens have
always carried `type` (`ACCESS`/`REFRESH`, `SecurityConstants.CLAIM_TYPE` — pre-existing, not
introduced by either fix pass), so the information needed to distinguish them was always present
in the signed payload; it was simply never read at authentication time. Confirmed before this
pass: `Authorization: Bearer <refreshToken>` against `GET /api/v1/auth/me` returned **200**.
`backend/app`'s `SpikeJwtAuthenticationFilter` never had this gap — it already rejects
`type != ACCESS` — because it was designed as a from-scratch aggregation-era filter, not carried
over from the microservice-era `JwtAuthenticationFilter` this fix targets.

**Fix — two small, separate changes:**

1. **`config/JwtAuthenticationFilter`**: after successfully parsing a bearer token (signature +
   basic structure valid — i.e., past the existing malformed/wrong-signature `catch (JwtException)`
   boundary, untouched), the filter now also extracts the `type` claim and requires an **explicit
   positive match** against `SecurityConstants.TOKEN_TYPE_ACCESS` before authenticating. A
   `REFRESH`-type token, or any unrecognized/missing type, simply **does not authenticate** — the
   filter does not write a response or short-circuit the chain, it just leaves
   `SecurityContextHolder` empty and calls `filterChain.doFilter(...)`, identical to what already
   happens when no bearer token is presented at all.
2. **`config/SecurityConfig`**: added an `AuthenticationEntryPoint` (mirroring `backend/app`'s
   `SpikeSecurityConfig.unauthorizedEntryPoint()` almost verbatim) so that Spring Security's own
   `anyRequest().authenticated()` rule returns **401**, not its 403 default, whenever a request
   reaches a protected endpoint with no `Authentication` in the context — which now includes both
   "no bearer token" and "wrong-type bearer token" cases uniformly.

**Design decision — why not mirror `backend/app`'s immediate-401 rejection exactly:**
`backend/app`'s filter can safely reject a wrong-type bearer token outright because that service
never accepts a refresh token in any form, anywhere. auth-service is different: it **owns**
`POST /api/v1/auth/refresh-token`, a `permitAll()` endpoint whose entire purpose is to receive a
refresh token — copying the immediate-rejection pattern verbatim would have created a filter that
could, in principle, reject a call to that endpoint before it ever reached the controller, purely
based on what the caller happened to put in the `Authorization` header (irrespective of the actual
refresh token, which travels in the request body, not as a bearer). The chosen design — decline to
authenticate, let the endpoint's own `permitAll()`/`authenticated()` rule decide — is safe for
`/auth/refresh-token`, and every other current or future `permitAll()` path, **by construction**,
not merely because today's frontend happens not to trigger the bad case. See the filter's own
javadoc for the same reasoning in the code.

**Backward compatibility (Requirement 9's decision, documented as asked):** every access token
this service has *ever* issued has always set `type=ACCESS` — that claim predates both fix passes.
There is no historical population of previously-issued access tokens missing the claim, so no
migration or grace-period logic was needed. The check is deliberately an explicit positive match
(`TOKEN_TYPE_ACCESS.equals(type)`), not `!TOKEN_TYPE_REFRESH.equals(type)` — an unknown or missing
`type` is treated as **insufficient to authenticate**, never as an implicit access grant, exactly
as Requirement 9 required.

**Verification, both automated and live:**

- **New regression test**: `config/JwtAuthenticationFilterTest` (9 tests, new file) — calls the
  real filter directly (`doFilterInternal`) with a real `JwtService` (so tokens carry real claims)
  and a mocked `UserDetailsServiceImpl`. Covers: access token authenticates; refresh token does
  **not** authenticate (the core regression, and confirms `userDetailsService` is never even
  queried for a wrong-type token); no header is a no-op; expired access/refresh tokens are
  rejected; malformed and wrong-signature tokens are rejected; a token with a garbage `type` value
  does not authenticate either (proves the positive-match design, not just "excludes REFRESH").
- **Proved the tests are meaningful**: reverted `JwtAuthenticationFilter.java` and
  `SecurityConfig.java` to their pre-this-pass content (`git stash`) and reran — 2 of the 9 new
  tests failed (`refreshTokenDoesNotAuthenticate`, `unknownTokenType_doesNotAuthenticate`), exactly
  the ones targeting this gap; all pass with the fix. Fix restored before continuing.
- **Live end-to-end, real auth-service + real Postgres**: `/me` with an access token → 200; `/me`
  with a refresh token → **401** (was 200); `/logout` with a refresh token → 401; an access token
  presented to `/refresh-token` → 401 (unaffected, pre-existing mechanism); a refresh token with
  its `type` claim hand-edited to look like something else, re-encoded **without** re-signing
  (attacker has no secret) → 401, because the signature no longer verifies — concretely
  demonstrating that a `type` claim is cryptographically protected exactly as Requirement 3
  required, not just conventionally trusted.
- **Cross-service, real `backend/app` instance**: auth-generated access token → protected
  `backend/app` endpoint → 200 (unaffected); auth-generated refresh token → same endpoint → 401
  (unaffected, `SpikeJwtAuthenticationFilter` already correct).
- **Full forged-header + JWT regression re-run, both services**: forged `X-User-Id`,
  `X-User-Roles`, `X-Username`, `X-Tenant-Id`, forged `X-Internal-Auth`, malformed token,
  wrong-signature token — all still correctly rejected (401). CORS re-verified on both services
  (allowed origin → 200 with credentials; disallowed → 403).
- **Byproduct fix, live-confirmed**: the previously-flagged cosmetic gap ("auth-service's own `/me`
  returns 403, not 401, for a completely missing Authorization header" — pass 2's Known Limitation
  #4) is now also resolved, since the same new `AuthenticationEntryPoint` handles both cases
  uniformly. Not a separate change — a direct consequence of the fix above.
- **Test suite**: auth-service 84/84 real tests pass (9 new + 75 pre-existing, unchanged), 1
  pre-existing environment-only Testcontainers failure (unrelated — see §"Test execution" below).
  `mvn -pl app verify`: 42/42, unchanged.

### 13.3 Concurrent refresh — verified, not changed

**Live-verified against the real running service + real Postgres**: 10 truly concurrent HTTP
`POST /refresh-token` calls presenting the **identical** refresh token produced exactly **1
success (200)** and **9 correctly-rejected (401)**, with only one row for the original hash
(`revoked=true`) and exactly one new row minted. **The intended single-use/rotation semantics hold
under real concurrency**, even though the code has no explicit `SELECT ... FOR UPDATE` or
`@Version` optimistic-locking column — Postgres's per-statement snapshot plus the row lock taken
by the `UPDATE` issued on revoke appears to close the practical race window in this environment.
This is an **empirical result, not a formal guarantee** — no locking was added (the concurrency
policy was not silently changed, per the task's instruction), and this result should not be read as
proof against all possible timing/load conditions, only as confirmation that the observed behavior
today matches the intended single-use policy. A Mockito-mocked "concurrency" unit test would not be
meaningful here (mocks have no real transactional/row-locking semantics), so this was verified
live rather than faked as an automated test — see `RefreshTokenServiceLayerRegressionTest`'s class
Javadoc for the same explanation in the test suite itself.

## 14. Token rotation

Verified end-to-end, both live (real DB) and via the new automated test
(`RefreshTokenServiceLayerRegressionTest$Rotation`): refresh token A → `/refresh-token` → access
token B + refresh token C, with A immediately revoked; presenting A again → rejected (401 /
`AuthException`); presenting C → accepted, itself rotating to a fourth pair. Unchanged by the fix
except that A and C are now guaranteed never to collide with any other token issued for the same
user, including ones issued in the same second.

## 15. Environment variables

Unchanged from `docs/ENVIRONMENT_VARIABLES.md` — no new variables were introduced by the collision
fix (it is pure application logic, not configuration) or by the frontend timeout change (it is a
literal constant in `authAxiosClient.ts`, not env-driven, since it is not an environment-specific
value). Re-inspected this pass for completeness against the checklist:

| Variable | Documented? | Secret committed? |
|---|---|---|
| `JWT_SECRET` | Yes, both services, coordinated-value note | No — default is the known dev placeholder only |
| `CORS_ALLOWED_ORIGINS` | Yes, both services, coordinated-value note | No — default is local dev origins only |
| `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` (DB credentials) | Yes, both services | No — defaults are local dev only |
| `GOOGLE_CLIENT_ID` | Yes | No — default blank |
| Google client secret | **N/A — does not exist anywhere in this codebase.** ID-token verification only (`GoogleTokenValidator`); no authorization-code exchange, so no client secret is ever needed. Documented explicitly as such in `ENVIRONMENT_VARIABLES.md`. | — |
| `VITE_AUTH_API_URL` | Yes | No — unset locally by design |
| `VITE_BACKEND_API_URL` | Yes | No — unset locally by design |

No secrets are committed anywhere in this repository as a result of this pass.

## 16. Deployment sequence

Unchanged from `docs/FREE_DEPLOYMENT_ARCHITECTURE.md` §17.

## 17. Rollback plan

Unchanged from `docs/FREE_DEPLOYMENT_ARCHITECTURE.md` §18, plus: the `jti` fix is purely additive
to the JWT payload (an extra claim) — a rollback to the pre-fix auth-service build would resume
minting refresh tokens without `jti`, which remain structurally valid and continue to work with
the (unchanged) hash-based lookup; there is no data migration to undo, since `jti` was never
persisted as its own column.

## 18. Known limitations

1. **CPU latency under Render Free's 0.1 vCPU** (§10) — real, measured, **not fixed** (by
   instruction — BCrypt cost must not change); client-side timeout mitigation applied for the
   *warm* case only, cold-start/spin-down remains unaddressed. **The only remaining blocker — see
   §19.**
2. **Concurrent-refresh single-use guarantee is empirically verified, not formally proven** (§13.3)
   — no row-level locking exists in the code; today's real-world test held, but this is not a
   mathematical guarantee under all future load patterns. Not a blocker (nothing indicates it is
   currently broken), noted for awareness.
3. `backend/app`'s 12-service memory footprint remains a Group 6 "WARNING" under heavy concurrent
   export load (94–99% of 512 MB) — unchanged, not re-addressed this pass.
4. Render's exact free-tier CPU/RAM/spin-down/instance-hour specifics were **not independently
   re-verified against current Render documentation** in this task — Neon's connection limit *was*
   verified (§8), Render's was not.

**Resolved this pass** (no longer limitations): auth-service's own bearer filter accepting a
refresh token as an access token (§13.2 — fixed, tested, live-verified); auth-service's own `/me`
returning 403 instead of 401 for a completely missing Authorization header (§13.2 — fixed as a
direct byproduct of the same change, not a separate effort).

## 19. Remaining blockers

**One item** should get an explicit decision before real production deployment:

1. **CPU cold-start/latency behavior on Render Free** (§10) — a real, measured UX/availability
   limitation with no code fix possible (BCrypt cost must not change per instruction). Needs a
   product decision: accept the risk, add a keep-alive ping, add a loading-state UX, or move
   auth-service to a paid tier with more CPU.

The refresh-token-as-bearer-access gap that was the other blocker after pass 2 **is now fixed,
tested, and live-verified** (§13.2) — it is no longer a blocker. Everything else checked across
both passes — both refresh-token fixes, CORS, the full JWT contract (including token-type
confusion variants), forged-header protections, connection pool budget, memory in both 512 MB
containers, and the full `mvn -pl app verify` / auth-service test suites — is clean.

## 20. Final readiness status

# **READY WITH CONDITIONS**

Both refresh-token issues raised across this pass and the previous one are now **fixed,
automated-test-covered, and live-verified**: the collision bug (§13.1) and the
refresh-token-as-bearer-access gap (§13.2). Per the task's own gate — "do not claim READY until...
refresh token cannot authenticate `/me`... access token still works... rotation still works...
revoked refresh tokens remain rejected... all existing JWT/header-forgery protections pass...
auth 512 MB test passes... relevant test suite passes" — **every one of those conditions is now
met**, verified in this pass specifically (§13.2, §9, and the "Test execution" section below).

Readiness remains conditional on exactly **one** genuine item: the pre-existing Render Free
CPU/cold-start limitation (§10, §19) — not a code defect, not fixable without either changing
BCrypt cost (explicitly disallowed) or a product-level decision (keep-alive ping, loading-state
UX, accepted risk, or a paid tier). Nothing else discovered in either pass remains open.
