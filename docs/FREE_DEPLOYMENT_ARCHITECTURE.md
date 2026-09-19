# Farm2Home — $0 Deployment Architecture (Vercel + Render Free + Neon)

Status: **implementation/configuration prepared and locally verified. Nothing deployed.**
Branch: `spike/render-single-jvm`. This document supersedes nothing — it builds on
`backend/app/DEPLOYMENT_DECISION.md`, which already recommended keeping auth-service standalone
(Option B) after the Group 1-6 single-JVM aggregation spike. That recommendation is now final
and implemented at the configuration level.

---

## 1. Architecture

```
                     ┌──────────────────────────┐
                     │   Vercel (React SPA)      │
                     │   VITE_AUTH_API_URL        │
                     │   VITE_BACKEND_API_URL     │
                     └───────────┬───────┬────────┘
                                 │       │
                 CORS-gated,     │       │   CORS-gated,
                 direct browser  │       │   direct browser
                 HTTPS call      │       │   HTTPS call
                                 ▼       ▼
          ┌──────────────────────┐   ┌───────────────────────────┐
          │  auth-service         │   │  backend/app                │
          │  Render Free           │   │  Render Free                 │
          │  512 MB / 0.1 CPU      │   │  512 MB / 0.1 CPU            │
          │  port $PORT            │   │  port $PORT                  │
          │  standalone Spring Boot│   │  12-service single-JVM        │
          │  (own JWT filter, own  │   │  aggregate (farm, production, │
          │  BCrypt, own Flyway)   │   │  customer, inventory,         │
          └───────────┬────────────┘   │  subscription, order, payment,│
                      │                 │  delivery, notification,      │
                      │ same JWT_SECRET │  invoice, dashboard, reports) │
                      │ (verify only,   │  own SpikeJwtAuthenticationFilter,│
                      │  never signs)   │  own Flyway (10 schemas)      │
                      │                 └───────────────┬────────────────┘
                      │                                 │
                      ▼                                 ▼
              ┌─────────────────────────────────────────────────┐
              │                Neon Postgres (free)                │
              │  schema "auth" (auth-service)                       │
              │  schemas farm/production/customer/inventory/         │
              │  subscription/order/payment/delivery/notification/   │
              │  invoice (backend/app)                                │
              └─────────────────────────────────────────────────┘
```

There is **no runtime gateway** in this architecture (the microservice-era `api-gateway` module
is not part of this deployment). Each of the two Render services:

- binds to `$PORT` (Render's convention — never a hardcoded port),
- answers its own CORS preflight (`CorsConfig` / `SpikeSecurityConfig`'s `corsConfigurationSource`
  bean — neither service had CORS configured before this work, since both previously relied on
  the gateway's central `CorsConfig`),
- validates JWTs itself (auth-service's own filter mints + validates; `backend/app`'s
  `SpikeJwtAuthenticationFilter` validates only — it never mints a token),
- requires no Eureka, no Config Server, no Kafka broker, no Redis to start or serve traffic.

## 2. Vercel (frontend)

- Static React SPA build (`npm run build` → `tsc && vite build`).
- Two Vite env vars select the absolute origins of the two backends (`VITE_AUTH_API_URL`,
  `VITE_BACKEND_API_URL`). Local dev leaves both unset — `axiosClient`/`authAxiosClient` then fall
  back to the existing relative `/api/v1` path, which the Vite dev proxy still forwards to the
  local api-gateway on port 8095 (see `vite.config.ts`). **Local dev is completely unaffected.**
- No Vercel `rewrites` are used (see §8 for why) — the frontend calls each Render service
  directly, cross-origin, and CORS is what makes it work.
- `VITE_GOOGLE_CLIENT_ID` must equal auth-service's `GOOGLE_CLIENT_ID` (public value, not a
  secret — see §12).

## 3. auth-service

Standalone Spring Boot service. Inspected completely (Dockerfile, `application.yml`,
`SecurityConfig`, `JwtAuthenticationFilter`, `JwtService`, `JwtSecretGuard`, `OtpService`,
`OtpConfig`, `GoogleAuthConfig`/`GoogleTokenValidator`, `KafkaConfig`, `CustomerEventProducer`/
`OtpEventProducer`). No business logic was changed. What changed, and why:

| File | Change | Why |
|---|---|---|
| `application.yml` | `server.port: ${PORT:8081}` | Render sets `$PORT`; a hardcoded port would not bind. |
| `application.yml` | datasource `url`/`username`/`password` now `${SPRING_DATASOURCE_*:...}` (previously hardcoded to `localhost`) | Environment-driven, Neon-ready; local default unchanged. |
| `application.yml` | `eureka.client.enabled: ${EUREKA_CLIENT_ENABLED:true}` | Must be disableable for Render; default `true` preserves existing local/docker-compose behavior. |
| `application.yml` | Hikari `maximum-pool-size`/`minimum-idle`/`connection-timeout`/`idle-timeout`/`max-lifetime` now env-driven | Neon connection budget (§11); local defaults unchanged. |
| `application.yml` | `spring.kafka.bootstrap-servers` now `${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` | Already lazy/non-fatal (see §13); made overridable for consistency. |
| `application.yml` (new) | `farm2home.cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:...}` | New — auth-service had **no** CORS config; it relied entirely on the gateway. |
| `config/CorsConfig.java` (new) | `CorsConfigurationSource` bean, exact-origin allowlist, `allowCredentials=true`, never wildcard | Answers the service's own preflight. |
| `config/SecurityConfig.java` | `.cors(cors -> cors.configurationSource(corsConfigurationSource))` added to the filter chain | Wires the new CORS config in. |
| `application-render.yml` (new) | Eureka off, smaller Hikari pool defaults, `farm2home.observability.config-server-health.enabled: false`, `health.show-details: never` | Bundles the Render-shaped defaults behind `SPRING_PROFILES_ACTIVE=render` so a bare profile flag is enough (every value is still independently overridable via its own env var). |
| `Dockerfile` | `JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"` (was: no JVM flags at all) | Container-aware memory; measured (§13), not guessed. **Deliberately not** a copy of `backend/app`'s tuning (Netty arena caps, `-Xss512k`) — auth-service has no Netty/WebFlux traffic and no async fan-out, so those flags would be solving a problem this service doesn't have. |

**A real bug was found and fixed as part of this work** (config-only, not business logic): common
`ConfigServerHealthIndicator` (shared with `backend/app`, `common-observability`) defaults to
enabled and pings `http://localhost:8888/actuator/health`. auth-service never actually imports
config from Config Server (no `spring.config.import=configserver:` anywhere in the service — it
was only ever pinged for this health-check side effect), but with the indicator on,
`/actuator/health` was permanently `DOWN` in any environment without a Config Server —
which is every environment in this architecture. Fixed by disabling it in `application-render.yml`
(`farm2home.observability.config-server-health.enabled: false`), the identical fix already applied
to `backend/app`. **Verified**: before the fix, a fresh container's `/actuator/health` was
`{"status":"DOWN",...,"configServer":{"status":"DOWN",...}}`; after, `{"status":"UP"}`.

### 3.1 What auth-service does NOT need (and does not require) on Render

| Infra | Required? | Why |
|---|---|---|
| Eureka | No | `EUREKA_CLIENT_ENABLED=false` (or `SPRING_PROFILES_ACTIVE=render`). No service discovery in this architecture — both services are called by absolute URL. |
| Config Server | No | Never imported config from it; the health-check side effect is now disabled. |
| Kafka broker | No | `CustomerEventProducer`/`OtpEventProducer` are `@Async` with `ProducerConfig.MAX_BLOCK_MS_CONFIG=1500` on both explicit `ProducerFactory` maps — a send with no broker logs a failed future and never blocks the HTTP response (register/send-otp complete normally). The `CUSTOMER_CREATED`/OTP-event projections stay dormant, an already-accepted limitation (Kafka has never been available in this project's dev environment either). |
| Redis | No | Not used anywhere in auth-service. |
| Local PostgreSQL | No | `SPRING_DATASOURCE_URL` points at Neon. |

## 4. backend/app

**No business logic changed.** Two additions only:

1. `SpikeSecurityConfig` gained a `CorsConfigurationSource` bean (it had **none** at all — the
   Group 1-6 aggregation spike never needed one because CORS was always the gateway's job) and
   `.cors(...)` wired into the chain, driven by the same `CORS_ALLOWED_ORIGINS` env var / new
   `farm2home.cors.allowed-origins` property (default matches the local Vite origins).
2. Hikari pool size/idle/timeouts made independently env-overridable (`DB_POOL_MAX_SIZE`, etc.) —
   same mechanism as auth-service, for the shared Neon connection budget (§11).

Everything else — the 12-service single-JVM aggregation itself, `SpikeJwtAuthenticationFilter`,
`InternalCallToken`, `LoadBalancerClientConfig`, the `KafkaDisabledConfig`/`SchedulingConfig`/
`SpikeFlywayConfig` infrastructure — is unchanged from the Group 6 state (`GROUP6_REPORT.md`,
`DEPLOYMENT_DECISION.md`). `backend/app`'s own JVM tuning (`MaxRAMPercentage=55`,
`-Xss512k`, Netty arena caps) is untouched.

## 5. Neon (database)

One Neon Postgres instance serves both services via two separate `SPRING_DATASOURCE_URL`
connections (Hikari pools are per-process; there is no shared pool across the two Render
services). `backend/app` owns 10 schemas; auth-service owns the `auth` schema. No schema change,
no new database. See §11 for the connection-count math.

## 6. JWT flow

Both services already used the **identical** contract before any change here — verified, not
assumed:

| | auth-service (`JwtService`) | backend/app (`SpikeJwtAuthenticationFilter`) |
|---|---|---|
| Algorithm | HS256 | HS256 |
| Secret | `${JWT_SECRET}`, base64-decoded | `${JWT_SECRET}`, base64-decoded — **must be the identical value on both services** |
| Claims | `sub`=mobile, `userId`, `roles`, `type` (`ACCESS`/`REFRESH`), `iat`, `exp` | Reads the same 4 claims; rejects `type != ACCESS` |
| Access token expiry | 24h (`jwt.expiration`) | Enforced via `exp` claim only |
| Refresh token expiry | 7d (`jwt.refresh-expiration`) | N/A — backend/app never accepts a refresh token as a bearer (verified, see below) |
| Issuer/audience | Not used (neither service sets or checks `iss`/`aud`) | Same |

auth-service **mints**; `backend/app` only **verifies** (`Jwts.parser().verifyWith(...)` — no
`AuthenticationManager`, no `UserDetailsService`, no DB hit). This was already true before this
task — Option B in `DEPLOYMENT_DECISION.md` explicitly relied on it ("the token contract is
already interoperable across processes"); this task re-verified it live end-to-end.

**Live-verified matrix** (auth-service on a real 512 MB container → real JWT → `backend/app` on
a real local instance sharing the same `JWT_SECRET`):

| Scenario | Result |
|---|---|
| Register via auth-service → JWT → `GET /api/v1/cart` on backend/app (CUSTOMER-scoped) | **200** |
| Same JWT → `GET /api/v1/dashboard/summary` (SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER only) | **403** |
| No `Authorization` header → `GET /api/v1/cart` | **401** |
| Refresh token presented as a bearer access token | **401** (`type` claim check) |
| Malformed token (`not.a.jwt`) | **401** |
| Wrong-signature token (payload kept, signature corrupted) | **401** |
| Expired token (`exp` 60s in the past, correctly signed) | **401** on both auth-service's own `/me` and backend/app |
| Forged `X-User-Id`/`X-User-Roles: SUPER_ADMIN` with no bearer, no `X-Internal-Auth` | **401** (headers stripped before reaching the security filter) |
| Forged `X-Internal-Auth` (guessed value) + forged `X-User-*` | **401** (constant-time secret compare fails) |
| Real CUSTOMER JWT + forged `X-User-Roles: SUPER_ADMIN` riding along | **403** on the admin endpoint (forged header has zero effect — role comes only from the JWT `roles` claim) |

**Minor, pre-existing, out-of-scope note**: auth-service's own `/api/v1/auth/me` returns **403**
(not 401) for a request with *no* `Authorization` header at all (Spring Security's default
`AccessDeniedHandler`, since auth-service's `SecurityConfig` never customized an
`AuthenticationEntryPoint` the way `backend/app`'s `SpikeSecurityConfig` does). A *present but
invalid* token (malformed/expired/wrong-signature) correctly returns 401 either way, via the
filter's own explicit 401 write. This is a cosmetic inconsistency in auth-service's own error
codes, not a security gap, and was not in scope to change (no business/security logic was
touched). Flagging it here rather than silently leaving it undocumented.

## 7. CORS

Neither service had CORS configuration before this task (both relied on the api-gateway's single
`CorsConfig`, which does not exist in this deployment). Both now have their own
`CorsConfigurationSource`, driven by the same env var name (`CORS_ALLOWED_ORIGINS`, comma
separated), so the two must be **kept in sync** — set it to the identical value on both Render
services. Exact-origin matching only; **never** a wildcard with `allowCredentials=true`.

**Live-verified** (`OPTIONS` preflight against both services):

| | Allowed origin | Disallowed origin |
|---|---|---|
| auth-service `OPTIONS /api/v1/auth/login` | 200, `Access-Control-Allow-Origin` echoed, `Allow-Credentials: true` | 403 `Invalid CORS request` |
| backend/app `OPTIONS /api/v1/cart` | 200, origin echoed, `Allow-Credentials: true`, `Expose-Headers: Content-Disposition` | 403 `Invalid CORS request` |

## 8. Vercel routing

The frontend's HTTP layer (`frontend/src/services/`) already used **one shared axios instance**
(`axiosClient`, `baseURL: '/api/v1'`) for every call, auth included (`authService.ts` called
`/auth/login` etc. through the same client). That design assumed a single origin — true when a
gateway sits in front, false in this architecture.

**Chosen approach: two axios clients, not a Vercel rewrite.**

- `axiosClient.ts` was refactored into a `createApiClient(baseURL)` factory. The default export
  (used by every service file except `authService.ts`) now resolves `baseURL` from
  `import.meta.env.VITE_BACKEND_API_URL || '/api/v1'`.
- `authAxiosClient.ts` (new) is the same factory pointed at
  `import.meta.env.VITE_AUTH_API_URL || '/api/v1'`.
- `authService.ts`'s only change: imports `authAxiosClient` instead of `axiosClient`. Every path
  it calls (`/auth/login`, `/auth/register`, ...) is unchanged.
- Both env vars unset (local dev): both clients fall back to the same relative `/api/v1` path,
  routed by the existing Vite dev proxy to the local api-gateway — **zero behavior change
  locally**, confirmed via `npx tsc --noEmit` (clean) and by reading every other service file
  (24 files import the default `axiosClient` export unchanged).

**Why not a Vercel `rewrites` config** (the task's "if used, verify the exact rewrite behavior"):
Vercel's `vercel.json` rewrite `destination` is a **literal string** — it does not interpolate
project environment variables at request time. Using rewrites would mean hardcoding the two
Render URLs directly into a committed file, which the task explicitly says not to do before the
URLs are known, and would require a code change (or a build-time templating step) every time
either backend's URL changes. The env-driven dual-client approach achieves the same routing goal
(`/api/v1/auth/**` → auth-service, everything else → backend/app) purely through Vercel's
standard project environment variables, with no rewrite ordering to get right and no risk of
`/api/v1/auth/**` ever falling through to the generic `/api/v1/**` destination (each call site
already names its own axios instance — there is no shared prefix-matching to get wrong). If a
same-origin illusion is wanted later for some other reason, a `vercel.json` rewrite can still be
added on top of this — it is not precluded, just not necessary and not implemented here to avoid
the hardcoding tradeoff.

**No duplicate `/api/v1` prefix risk**: both clients still send paths beginning `/auth/...` (auth)
or the service's own root (`/cart`, `/orders`, ...) relative to their respective `baseURL`, which
already ends in `/api/v1`. Nothing changed about the path shapes, only which origin they resolve
against.

## 9. Render environment variables

See `docs/ENVIRONMENT_VARIABLES.md` for the full list with no values. Summary:

**auth-service**: `JWT_SECRET` (required, ≥256-bit base64, shared with backend/app),
`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` (Neon), `CORS_ALLOWED_ORIGINS`,
`SPRING_PROFILES_ACTIVE=render,prod` (the `prod` profile activates `JwtSecretGuard`'s fail-closed
check — refuses to start with a blank or still-default `JWT_SECRET`), `EUREKA_CLIENT_ENABLED=false`
(redundant with the `render` profile, but explicit), `GOOGLE_CLIENT_ID` (optional — blank
disables Google Sign-In cleanly, see §12), `DB_POOL_MAX_SIZE`/others (optional, §11).

**backend/app**: `JWT_SECRET` (same value), `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`
(Neon), `CORS_ALLOWED_ORIGINS` (same value as auth-service's), `DB_POOL_MAX_SIZE` (§11).
Eureka/Config Server/Kafka were already disabled by the Group 1-6 spike (`application.yml`
defaults) — no new variables needed there.

**Frontend (Vercel)**: `VITE_AUTH_API_URL`, `VITE_BACKEND_API_URL` (both the Render service's
public HTTPS URL), `VITE_GOOGLE_CLIENT_ID` (must equal auth-service's `GOOGLE_CLIENT_ID`),
`VITE_GOOGLE_MAPS_API_KEY` (pre-existing, unrelated to this task).

No final URLs are known yet, so none are hardcoded anywhere — every one of the above is read from
the environment at runtime (backend) or build time (frontend/Vite).

## 10. Neon environment variables

`SPRING_DATASOURCE_URL` should be Neon's pooled-connection endpoint (`...neon.tech/<db>?sslmode=require`,
using the `-pooler` hostname Neon provides) rather than the direct endpoint, given two independent
Render services each opening their own Hikari pool against one free-tier Postgres (§11).

**Verified against Neon's current official docs** (neon.com/docs/connect/connection-pooling,
fetched during this task): the free tier's default compute size (0.25 CU) sets Postgres
`max_connections=104`, of which 7 are reserved for the Neon superuser, leaving **97 usable** on
the **direct** connection string. The **pooled** (`-pooler`, PgBouncer) endpoint's effective limit
is `default_pool_size = 0.9 × max_connections` ≈ **94 concurrent active connections**, with
PgBouncer itself able to multiplex up to 10,000 client-facing connections down onto that pool.
Both figures comfortably cover the worst-case total below.

## 11. Database connection pool strategy

Each Render service is its own process with its own Hikari pool — there is no shared pool.
Worst-case simultaneous connections to Neon:

| Service | `maximum-pool-size` default (local) | Recommended for Render (`DB_POOL_MAX_SIZE`) | Rationale |
|---|---|---|---|
| `backend/app` | 10 | **6–8** | 12 services share one pool already (Group 1-6 design); low free-tier traffic doesn't need 10. |
| auth-service | 10 (`render` profile default: **5**) | **4–5** | Low-QPS (login/register/refresh/OTP only); already defaulted down in `application-render.yml`. |
| **Worst-case total** | — | **~10–13** | **Verified safe**: ~11–14% of the pooled endpoint's ~94-connection budget (§10), ~13% of the direct endpoint's 97-usable budget. Wide headroom even if both services later need larger pools. |

`minimum-idle` defaults to 0 on `backend/app` (already) and is set to 0 in auth-service's `render`
profile (was 2 locally) — idle connections are not held open against the free-tier database
between requests. `connection-timeout`/`idle-timeout`/`max-lifetime` are all now independently
env-overridable on both services (`DB_POOL_CONNECTION_TIMEOUT_MS`, `DB_POOL_IDLE_TIMEOUT_MS`,
`DB_POOL_MAX_LIFETIME_MS`) with their prior defaults preserved. No schema change, no new database.

## 12. Memory configuration

See §13 for the actual measured numbers. Summary of what changed:

- auth-service's Dockerfile went from **zero** JVM flags to
  `-XX:MaxRAMPercentage=60 -XX:+UseSerialGC`, chosen and then measured (not the reverse).
- `backend/app`'s existing tuning (`MaxRAMPercentage=55`, `-Xss512k`, Netty arena caps,
  `GROUP6_REPORT.md`) is untouched — this task did not re-measure `backend/app`'s 12-service
  aggregate, since Group 6 already did so exhaustively and no code changed there beyond the CORS
  addition (a handful of extra beans, negligible against the existing ~494–505 MiB measured peak).

## 13. Measured results (this task)

All measurements below are real (`docker run --memory=512m --memory-swap=512m`), not estimates —
Docker was confirmed available and used.

### auth-service, unconstrained CPU, 512 MB memory limit

| Point | RSS / 512 MB | % |
|---|---|---|
| Startup (health UP) | 289.5 MiB | 56.6% |
| After 25 concurrent registrations (BCrypt cost=12 each) | 322.9 MiB | 63.1% |
| End of full functional test session (register/login/OTP/refresh/logout/me repeated) | 332.8 MiB | 65.0% |
| OOMKilled | **false** | — |

### auth-service, Render-realistic constraint (`--cpus=0.1 --memory=512m`)

| Point | Value |
|---|---|
| Cold start to healthy (`/actuator/health` = UP) | **~6m23s** (383s) |
| First request after cold start (`POST /register`, BCrypt cost=12) | **30.1s** |
| Second request on the now-JIT-warmed JVM (same endpoint) | **8.2s** |
| Memory after this session | 305.9 MiB / 512 MB (59.75%) |
| OOMKilled | **false** | — |

**This is the single most important finding of this task.** auth-service comfortably fits in
512 MB memory (Target from Step 10: met, with ~35–47% headroom in every scenario tested). It does
**not** comfortably fit Render Free's CPU budget: a ~6.4-minute cold start and an 8-30 second
single login/register request are both far outside normal user-facing expectations, and the
frontend's own axios client currently times out at **10 seconds** (`axiosClient.ts`,
`timeout: 10000`) — meaning a cold-started or even a warm-but-loaded auth-service would show the
user a client-side timeout error on login/register under Render Free's actual CPU allocation, not
a slow-but-working request. BCrypt's cost factor was **not** weakened to make this number look
better, per the task's explicit instruction — this is reported as a deployment limitation, not
fixed.

### backend/app

Not re-measured in this task — Group 6 (`GROUP6_REPORT.md`) already measured the 12-service
aggregate exhaustively (~355 MiB startup, ~482–505 MiB peak under heavy export load, 94–99% of
512 MB, "WARNING bordering RED"). The only code change here (a CORS bean + a handful of
env-var-driven config beans) is negligible against that footprint and was not felt worth an
independent re-run; Group 6's conclusion stands unchanged.

## 14. Kafka-disabled behavior

Both services already handle "no broker reachable" without failing requests or blocking startup —
this was true before this task (auth-service: `MAX_BLOCK_MS_CONFIG=1500` + `@Async`; `backend/app`:
`KafkaDisabledConfig` stops listener containers, producers are lazy with their own per-service
`max.block.ms`). Kafka has been unavailable throughout this project's entire dev environment, so
this behavior has been exercised continuously, not just in this task. auth-service's dormant flows
under no broker: `CUSTOMER_CREATED` projection (customer-service never receives the event — no
functional impact on auth itself, a pre-existing, accepted limitation of the whole platform's
event-driven paths) and the OTP delivery event (SMS itself is unaffected — `SmsService` uses the
`logging` provider directly, not Kafka; only a secondary notification-service projection is
dormant).

## 15. External integrations

- **Google Sign-In** (auth-service): fails closed when `GOOGLE_CLIENT_ID` is unset — the service
  starts normally either way (the `GoogleIdTokenVerifier` is built lazily, not as a `@Bean`, so an
  absent client ID never touches application startup); only the first actual `POST /auth/google`
  call returns a clean 401 ("Google Sign-In is not configured on this server."). No client secret
  is used anywhere (ID-token verification only). See §16 for the frontend implication.
- **SMS (OTP delivery)**: `sms.provider=logging` by default (no external calls, no credentials) —
  unchanged, already the default in every environment this project has run in.
- **Razorpay** (backend/app, payment-service): unaffected by this task; `payment.gateway.provider`
  defaults to `mock` (no external HTTP, no credentials) — this task did not touch payment
  configuration.

## 16. Free-tier limitations (accepted, not fixed in this task)

1. **auth-service CPU under Render Free's 0.1 vCPU is the binding constraint**, not memory (§13).
   A cold start takes minutes, not seconds; a single BCrypt(12) login/register can take
   single-digit-to-tens of seconds depending on JIT warmth. This will visibly affect first-login
   UX, especially combined with Render Free's spin-down-after-inactivity behavior (exact spin-down
   interval was not independently re-verified against Render's current docs — confirm before
   deploying). Documented, not fixed — BCrypt's work factor was deliberately left unchanged per
   the task's instructions, and any fix (raising the frontend axios timeout, a loading-state UX
   change, keep-alive pinging, or a paid instance) is a product/infra decision outside this
   phase's scope.
2. **`backend/app`'s 12-service memory footprint remains a Group 6 "WARNING"** under heavy export
   concurrency (94–99% of 512 MB) — unchanged by this task, not re-fixed here (Group 6 already
   proposed mitigations: streaming PDF export, capped export row counts, or moving to a
   768 MiB–1 GiB instance).
3. **FIXED (see `docs/PRODUCTION_READINESS.md` §13 for full detail)**: the refresh-token collision
   bug originally flagged here (byte-identical refresh tokens within the same wall-clock second,
   due to no `jti` + jjwt's integer-second `iat`/`exp` truncation, silently breaking the
   single-use/revocation guarantee) was fixed in a follow-up pass: `JwtService.generateRefreshToken`
   now stamps a cryptographically random `jti` (RFC 7519 JWT ID) on every refresh token, making the
   compact token string — and therefore its stored SHA-256 hash — unique per issuance, including
   concurrent issuances for the same user. Live-reverified after the fix (same DB-trace method):
   no collision, and reusing an already-rotated token now correctly returns 401. A 10-way true
   concurrent-request test against the real service also confirmed single-use semantics hold
   (exactly 1 of 10 simultaneous identical-token refresh calls succeeded). See
   `docs/PRODUCTION_READINESS.md` for the full before/after evidence and the new regression test
   suite (`JwtServiceTest$RefreshTokenCollisionRegression`,
   `RefreshTokenServiceLayerRegressionTest`).
4. **NOT fixed, newly found in the same follow-up pass**: auth-service's own bearer-auth filter
   (`config/JwtAuthenticationFilter`) does not check the JWT `type` claim at all — only subject
   match + expiry (`JwtService.isTokenValid`). Live-verified: a refresh token presented as
   `Authorization: Bearer <refreshToken>` to auth-service's own `GET /api/v1/auth/me` returns
   **200**, not 401. This is distinct from (and in addition to) the collision bug above — a leaked
   refresh token (7-day lifetime vs. the access token's 24h) can be used directly as a bearer
   credential against auth-service's own endpoints, bypassing the intended access/refresh
   separation. `backend/app`'s `SpikeJwtAuthenticationFilter` does **not** have this gap (it
   explicitly rejects `type != ACCESS`, live-verified both before and after this fix). Flagged, not
   silently patched — see `docs/PRODUCTION_READINESS.md` §13 for the recommended fix and why it was
   left to an explicit decision rather than fixed in-place.
5. auth-service's own `/api/v1/auth/me` (and other protected endpoints) return 403 rather than 401
   for a *completely missing* Authorization header (§6) — cosmetic, pre-existing, not fixed.
6. Render's exact free-tier CPU/RAM/spin-down/instance-hour specifics were **not independently
   re-verified against current Render docs** in this task (§13) — confirm before finalizing
   instance sizing. (Neon's connection limit **was** verified — see §10/§11, updated in the
   follow-up pass against neon.com/docs/connect/connection-pooling.)

## 17. Deployment sequence (when actually deploying — not done in this task)

1. Provision Neon Postgres; get the pooled connection string.
2. Create the Render Web Service for auth-service: build from `backend/auth-service/Dockerfile`,
   set env vars per `docs/ENVIRONMENT_VARIABLES.md`, `SPRING_PROFILES_ACTIVE=render,prod`.
3. Run auth-service's Flyway migrations (happens automatically on first boot — `flyway.enabled:
   true`, `create-schemas: true`).
4. Create the Render Web Service for `backend/app`: build from `backend/app/Dockerfile`, set the
   same `JWT_SECRET`, its own `SPRING_DATASOURCE_*`, `CORS_ALLOWED_ORIGINS` (same value as
   auth-service's).
5. Verify both services' `/actuator/health` return `{"status":"UP"}` independently.
6. Set the two Render URLs as `VITE_AUTH_API_URL`/`VITE_BACKEND_API_URL` in the Vercel project,
   plus `VITE_GOOGLE_CLIENT_ID` if Google Sign-In is wanted, and deploy the frontend.
7. Live-verify the full flow from a real browser: register/login on the Vercel origin → JWT →
   a `backend/app` protected call → 200; confirm no CORS error in devtools; confirm no
   `X-Internal-Auth` or gateway-owned header ever appears in a browser request (it shouldn't —
   nothing in the frontend code references it).
8. Repeat the JWT contract matrix from §6 against the real deployed URLs, not just localhost.

## 18. Rollback plan

Both services are independently deployable and independently revertible — Render redeploys are
per-service. auth-service's Flyway migrations are additive-only (no destructive migration was
touched or added by this task); reverting auth-service's code to a prior commit does not require
a database rollback. `backend/app`'s CORS addition is likewise additive (a new bean; no existing
route, filter order, or security rule was removed or narrowed) and reverts cleanly by redeploying
the prior image. If `CORS_ALLOWED_ORIGINS` is misconfigured post-deploy (e.g. wrong Vercel origin),
it is a same-service env-var fix + restart on the affected Render service only — no code rollback
needed. If `JWT_SECRET` drifts between the two services (the one genuinely coordinated,
two-service value), every previously-issued token becomes invalid on whichever service didn't get
the update; fix by resetting the mismatched service's env var to match and restarting it (existing
sessions re-authenticate on next login, no data loss).

## Known limitations (recap)

See §16 for the full list. The two that matter most for a go/no-go decision: **auth-service's CPU
behavior under Render Free's 0.1 vCPU quota** (cold start ~6.4 min, single request 8–30s) and the
**refresh-token collision defect** (real, reproduced, unfixed, needs an explicit decision — see the
final report).
