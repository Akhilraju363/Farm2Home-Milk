# Farm2Home — Deployment Architecture Decision: auth-service Placement

Branch: `spike/render-single-jvm` · 2026-09-08 · **Read-only analysis. Nothing implemented,
committed, or pushed. auth-service was NOT aggregated.**

Question: after Groups 1–6 (12 services in `backend/app`), how should **auth-service** be
deployed?

- **OPTION A** — move `backend/app` to a ≥ 1 GB instance and embed auth-service (Group 7 as
  aggregation → 13 services in one JVM).
- **OPTION B** — keep `backend/app` at 512 MB and run auth-service as a separate standalone
  service (Group 7 as deployment, not aggregation).

---

## 0. Measured inputs (Group 6, unchanged — no new runs)

| Metric | Value |
|---|---|
| Services in `backend/app` | 12 |
| App tests | 42/42 PASS; Groups 1–5 regression PASS |
| JVM flags (required) | `-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Xss512k -Dio.netty.allocator.num*Arenas=2` |
| Startup steady (`docker stats`) | **~355 MiB** |
| After heavy export workload | **~482 MiB** |
| Large / concurrent export peak | **~494–505 MiB** |
| RSS peak | **~511–528 MB** |
| % of 512 MB at peak | **94–99 %** |
| Default `MaxRAMPercentage=70` | OOM-killed under the same workload |
| Memory leak | none found |
| GC | SerialGC (G1 measured worse — +65 MB native) |
| Group 6 verdict | **PASS WITH CONDITIONS**; 512 MB = WARNING bordering BLOCKER |

**The 12-service aggregate already has essentially zero memory headroom on 512 MB.** This is the
single fact that dominates the decision.

---

## 1. auth-service — read-only structural inspection

Source: `backend/auth-service/**`. Nothing was built or run; figures for beans/memory are
**estimates from structure**, flagged as such.

### 1.1 Bean / component footprint

| | Count | Notes |
|---|---|---|
| `@Component`/`@Service`/`@Repository`/`@Configuration`/`@RestController`/`@ControllerAdvice` classes | **24** | |
| `@Bean` methods | **11** | |
| Spring Data JPA repositories | **5** | `UserRepository`, `RoleRepository`, `RefreshTokenRepository`, `OtpVerificationRepository`, `UserIdentityRepository` |
| Total artifacts in dependency tree | 216 | (aggregate is ~230) |
| **Estimated beans added to an aggregated context** | **~40–55** | 24 stereotypes + 11 `@Bean` + 5 repo proxies + Spring Security DAO machinery (`DaoAuthenticationProvider`, `AuthenticationManager`, `PasswordEncoder`) + `kafkaEventExecutor` |

### 1.2 JPA footprint

- **5 entities**: `User` (implements `UserDetails`; `@ManyToMany(fetch = EAGER)` → `Role` via
  `user_roles`), `Role`, `RefreshToken`, `OtpVerification`, `UserIdentity`. 3 enums
  (`RoleType`, `OtpType`, `IdentityProvider`).
- `default_schema = auth`; `ddl-auto: validate`; `@EnableJpaAuditing` (own `AuditorAwareImpl`,
  bean name `auditorAwareImpl` — **collides by name** with the aggregate's `SpikeAuditorAware`).
- `@EnableJpaRepositories(basePackages = {"com.farm2home.auth", "com.farm2home.common.core.audit"})`
  — the familiar "does not compose" problem: would have to fold into the aggregate's single
  `SpikeJpaConfig`.
- Adds **5 entity types + 1 EAGER collection** to the shared Hibernate metamodel/persister set.

### 1.3 Migrations

- **4** migrations: `V1__init_auth_schema` (5 tables: `users`, `roles`, `user_roles`,
  `otp_verifications`, `refresh_tokens` + 4 indexes), `V2__create_audit_log`,
  `V3__extend_audit_log`, `V4__social_login_and_otp_hardening`. 142 SQL lines total.
- New `auth` schema (`create-schemas: true`, `baseline-on-migrate: true`).
- Option A: an 11th per-schema Flyway bean (`authFlyway`) + 4 verbatim migration copies into
  `app/src/main/resources/db/migration/auth/` — the proven Group 1–6 pattern.

### 1.4 Security infrastructure

- `@EnableWebSecurity` + `@EnableMethodSecurity` + own `SecurityFilterChain`.
- **`DaoAuthenticationProvider`** + **`AuthenticationManager`** + **`UserDetailsServiceImpl`**
  (`implements UserDetailsService`, DB lookup by mobile) — a full username/password auth path.
  The aggregate currently **excludes** `UserDetailsServiceAutoConfiguration` and has **no**
  `AuthenticationManager` / `AuthenticationProvider`.
- Own `JwtAuthenticationFilter` — **does a DB `loadUserByUsername` on every authenticated
  request** (vs the aggregate's `SpikeJwtAuthenticationFilter`, which validates purely from JWT
  claims, no DB). In Option A this filter is *not* scanned; the app filter serves auth's
  endpoints — but that means `/auth/me` needs the entity principal loaded another way (§1.6).
- `JwtSecretGuard` — `@Profile`-gated startup guard requiring a non-default `JWT_SECRET` in prod.
- 6 new public endpoints on the **one** filter chain (§1.5).

### 1.5 JWT infrastructure

- `JwtService` — jjwt HS256, base64-decoded `${jwt.secret}`, mints **access (24 h)** +
  **refresh (7 d)** tokens. Claims: `userId`, `roles`, `type` (`ACCESS`/`REFRESH`), `sub` =
  mobile — **identical `SecurityConstants` claim names and signing** to what the aggregate's
  `SpikeJwtAuthenticationFilter` already validates.
  → **The token contract is already interoperable across processes today.** Both options share
  one `JWT_SECRET`.
- jjwt-api/impl/jackson are already on the aggregate's classpath (added for the spike). **No new
  JWT dependency.**
- Public (no-token) endpoints, verbatim from `SecurityConfig`:
  `POST /api/v1/auth/{register, login, send-otp, verify-otp, refresh-token, google}`.
  `GET /auth/me` and `POST /auth/logout` require a token.

### 1.6 BCrypt usage

- `BCryptPasswordEncoder(12)` — work factor **12** (2¹² = 4096 rounds ≈ **200–400 ms CPU per
  hash** on a shared/low vCPU).
- Called on: **register** (`encode` password), **login** (`DaoAuthenticationProvider` verify),
  **send-otp** (`encode` the 6-digit OTP — yes, OTPs are BCrypt-hashed), **verify-otp**
  (`matches`).
- **Memory cost: negligible** (a few KB work buffer per call). **CPU cost: significant** on a
  0.1-vCPU instance — a burst of logins/registrations serializes behind BCrypt and, in Option A,
  starves the request threads of the other 12 services in the same JVM.

### 1.7 OTP infrastructure

- `OtpService` + `OtpVerification` entity + `OtpConfig`/`OtpProperties`
  (`otp.expiry-seconds`/`max-attempts`/`resend-cooldown`/`max-resends`).
- `@Scheduled(fixedRate = 600_000)` — a 10-minute expired-OTP cleanup job (must be gated by the
  aggregate's `farm2home.scheduling.enabled` pattern in Option A).
- SMS delivery via common-core's SMS provider abstraction — **logging provider by default**
  (`sms.provider: logging`), already active in the aggregate since Group 1. No credentials.
- `OtpEventProducer` — Kafka, `@Async("kafkaEventExecutor")`.

### 1.8 `User` entity dependencies / `@AuthenticationPrincipal User`

- `@AuthenticationPrincipal User user` appears in **exactly one place** in the entire codebase:
  `AuthController.me()` → `GET /api/v1/auth/me`.
- `User` is a **mutable JPA `@Entity`** (not the `(UUID, String, Set<String>)` record every
  other service uses). The aggregate's `ServiceUserPrincipalArgumentResolver` matches
  `com.farm2home.*.config.UserPrincipal` **records only** — it will not resolve `User`.
- Option A therefore needs a **dedicated resolver branch**: on `@AuthenticationPrincipal User`,
  load the `User` by the JWT `userId` via `UserRepository` — **a DB round-trip on every
  `/auth/me` call**, plus loading a full mutable entity (with its EAGER `roles`) as the security
  principal.

### 1.9 External dependencies new to the aggregate

`com.google.api-client:google-api-client:2.7.0` pulls a **~6.4 MB jar closure**:

| Jar | Size | Concern |
|---|---|---|
| `com.google.guava:guava:33.1.0-jre` | **~3.0 MB** (~2000+ classes) | Large. Aggregate currently has only guava **19.0** transitively via eureka (inactive) → **version tension**. |
| `google-http-client:1.45.0` (+ `-gson`, `-apache-v2`) | ~0.3 MB | Apache **HttpClient 4.5.14 / HttpCore 4.4.16** — aggregate uses **HC5** (eureka); HC4 would be newly active if Google is used. |
| `google-oauth-client:1.36.0` | ~0.08 MB | |
| `grpc-context` / `grpc-api:1.66.0` | ~0.3 MB | |
| `opencensus-api:0.31.1` + contrib | ~0.37 MB | |

- **`GoogleTokenValidator` loads these lazily**: it fails closed at
  `properties.getClientId().isBlank()` **before** touching `GoogleIdTokenVerifier`. With
  `GOOGLE_CLIENT_ID` unset (the default), the heavy Google/guava classes **never load** → ~0
  runtime metaspace, but **~6.4 MB dead weight in the fat jar** (on-disk + a few MB of mmap'd
  page cache).
- With `GOOGLE_CLIENT_ID` set (real Google Sign-In), the first `POST /auth/google` class-loads
  guava 33 + google-http-client + grpc + opencensus → **estimated +8–20 MiB metaspace/code cache,
  permanent** for the process lifetime.

Also new: `@EnableKafka` + `KafkaConfig` (producer-only: `CustomerEventProducer`,
`OtpEventProducer`; `@Async` via a new `kafkaEventExecutor` `ThreadPoolTaskExecutor`) +
`@EnableScheduling` + `@EnableAsync` on the auth Application class + `google-api-client` +
`spring-security-crypto` BCrypt.

### 1.10 Expected memory impact of embedding auth (Option A)

| Component | Estimate | Basis |
|---|---|---|
| 5 entities in shared metamodel + 5 repos + ~45 beans + `authFlyway` | +5–9 MiB | Group 5 measured notification+invoice (3 entities, ~18 beans) at ~+12 MiB total incl. jars |
| Spring Security DAO auth machinery (`DaoAuthenticationProvider`, `AuthenticationManager`, `UserDetailsServiceImpl`, provider manager) + ~20 classes | +3–6 MiB | |
| `kafkaEventExecutor` thread pool + `@EnableKafka` infra | +2–5 MiB | thread stacks + Kafka producer classes (spring-kafka already present) |
| auth's own ~60–80 classes + jjwt (already present) + BCrypt | +3–5 MiB metaspace | |
| **Structural subtotal (Google unconfigured, no traffic)** | **~+15–25 MiB** | |
| **If `GOOGLE_CLIENT_ID` set** (first `/auth/google`) | **+8–20 MiB more, permanent** | guava 33 + google-http-client + grpc + opencensus class-load |
| Steady runtime (auth is low-QPS: login/register/refresh) | negligible | |
| BCrypt(12) per-op | ~0 memory / **200–400 ms CPU** | contention risk, not a memory risk |

**Projected Option-A peak on the current 512 MB config:**
`~505 MiB (Group 6 peak) + ~15–25 MiB (auth structural) = ~520–530 MiB` — **over 512 MB before
any Google load or export concurrency. Guaranteed OOM. Option A cannot run on 512 MB.**

---

## 2. Eighteen-factor comparison

Legend: **A** = embed in ≥1 GB aggregate · **B** = auth standalone, aggregate stays 512 MB.

### 2.1 Memory capacity

| | A | B |
|---|---|---|
| | 12-svc peak ~505 MiB + ~15–25 MiB auth + Google/export risk ⇒ **needs ≥ 1 GB** (768 MiB is the bare floor; 1 GiB comfortable). | Aggregate unchanged at ~505 MiB / 512 MB — **still the Group 6 WARNING** (independent of auth). auth standalone **~300–400 MiB** (estimate: JPA+Security+web service, comparable to farm/customer standalone) ⇒ comfortable on 512 MB, plausibly 384 MiB. |
| Verdict | A removes the aggregate's export-memory pressure only by paying for a bigger box; embedding auth *consumes* the new headroom rather than fixing the actual problem. | B keeps the two workloads' memory budgets separate. The aggregate's export risk remains and must be addressed regardless (Group 6). |

### 2.2 Startup time

| | A | B |
|---|---|---|
| | ~10 s now → **~11–13 s** (13 services + `authFlyway` 4 migrations + DAO auth wiring). One cold start wakes everything. | Aggregate ~10 s unchanged. auth standalone **~6–9 s** (small). Two independent starts. |
| Verdict | Marginally slower single start. | Two smaller starts; auth is fast. |

### 2.3 JWT / authentication flow

| | A | B |
|---|---|---|
| | `/auth/**` served in-process. Needs: wire `DaoAuthenticationProvider`/`AuthenticationManager`/`PasswordEncoder`/`UserDetailsServiceImpl` app-side; the `@AuthenticationPrincipal User` resolver branch (§1.8, DB hit per `/auth/me`); auth's own DB-per-request `JwtAuthenticationFilter` dropped in favour of `SpikeJwtAuthenticationFilter`. Refresh-token rotation (`refresh_tokens` table) moves in-process. | **Zero aggregate change.** auth keeps its own filter, DAO provider, refresh-token table. The aggregate already validates auth's token format (verified: identical claims + signing). Frontend gets its token from auth, sends `Bearer` to the aggregate — **works today**. |
| Verdict | New, security-sensitive wiring on the one shared filter chain. | The existing, tested flow. Nothing to build. |

### 2.4 Frontend routing

Frontend uses **one relative base**: `axiosClient.ts` → `baseURL: '/api/v1'` (auth, customers,
orders … all one origin). Production needs a rewrite from the Vercel origin to the backend.

| | A | B |
|---|---|---|
| | **One** rewrite: `/api/v1/* → https://<aggregate>.onrender.com/api/v1/*`. Simplest possible. | **Two ordered** rewrites: `/api/v1/auth/* → https://<auth>.onrender.com/...` **first**, then `/api/v1/* → https://<aggregate>.onrender.com/...`. Vercel supports this cleanly (path-prefix order). |
| Verdict | Trivial. | One extra rule, correct-by-ordering. Minor. |

### 2.5 CORS

**Gap in the current code (both options): the aggregate has NO CORS configuration** — it relied
on the api-gateway's `CorsConfig` (`FARM2HOME_FRONTEND_ORIGINS`), and **there is no gateway in
the Render deployment**. `common-web` provides no CORS bean. The `CorsFilter` slot in the
Security chain is a no-op without a `CorsConfigurationSource`.

| | A | B |
|---|---|---|
| | **One** CORS config to add (on the aggregate): one origin allowlist, one place. | **Two** CORS configs to add and keep in sync: the aggregate **and** auth-service (auth's `SecurityConfig` also has none — its comment says "CORS is handled once at the api-gateway"). Preflight `OPTIONS /api/v1/auth/login` must be permitted by auth's own chain. |
| Verdict | One config. | Two configs, same allowlist, drift risk. |

### 2.6 Internal service authentication

No internal service calls auth-service — it is a **leaf** (the frontend calls it; other services
only *validate* its tokens). auth's Kafka producers (CUSTOMER_CREATED, OTP events) are the only
"outbound", and they are dormant (no broker) in **both** options.

| | A | B |
|---|---|---|
| | Adds no new `lb://` loopback edge. | Adds no cross-container call (nobody calls auth). |
| Verdict | **No difference.** |

### 2.7 X-Internal-Auth

Group 5's per-process `InternalCallToken` (random secret, stamped on `lb://` loopback calls,
gates the `X-User-*` system-identity trust) concerns invoice/notification loopbacks. auth
participates in no `lb://` call.

| | A | B |
|---|---|---|
| Verdict | **No impact in either option.** The `X-Internal-Auth` model is unchanged. |

### 2.8 Database connections

One Neon Postgres (free tier — connection budget matters; **exact Neon free-tier limit REQUIRES
CURRENT EXTERNAL VERIFICATION**, historically pooled connections are needed).

| | A | B |
|---|---|---|
| | **One** Hikari pool (`spike-hikari`, max 10) serves all 13 schemas incl. `auth`. auth's own `maximum-pool-size: 10 / min-idle: 2` is ignored (config not scanned). **~10 connections total.** | **Two** pools: aggregate (max 10) + auth-service (max 10, **min-idle 2** → 2 idle connections held permanently). **Up to ~20 connections** to the same Neon instance. |
| Verdict | Half the connection footprint; single place to tune. | 2× connections, permanent idle holds — but 20 is likely within Neon free (verify). |

### 2.9 Flyway

| | A | B |
|---|---|---|
| | 11th `authFlyway` bean + 4 verbatim migration copies into `db/migration/auth/`. Proven pattern. Shared `AuditLog` still lands in `farm.audit_log` (Phase-10 `audit` schema still deferred). Startup runs 11 migration sets (~+0.3 s). | auth-service runs its **own** Flyway against `auth` (`create-schemas: true`, `baseline-on-migrate: true`) — fully independent, already works. Aggregate's 10 untouched. |
| Verdict | Manageable, but another schema folded into the aggregate's lifecycle. | Cleaner isolation — auth owns its schema end to end. |

### 2.10 Kafka-disabled architecture

Kafka has no broker in the $0 architecture (dormant in **both** options — env note: Kafka
permanently down here).

| | A | B |
|---|---|---|
| | auth `@EnableKafka` + 2 `@Async` producers → lazy/non-fatal (Group 1–6 pattern). No new consumer ⇒ `KafkaDisabledConfig` still stops exactly 3 factories. New `kafkaEventExecutor` pool. Auth→customer CUSTOMER_CREATED projection stays **dormant** (as it is standalone-on-Render anyway). | auth standalone also has no broker on Render → producers dormant. **Identical outcome.** |
| Verdict | **No difference** — the CUSTOMER_CREATED / OTP-event flows are already an accepted dormant limitation. |

### 2.11 Render deployment

**Render free-tier specifics (512 MB RAM, ~0.1 shared vCPU, spin-down after inactivity, monthly
instance-hour budget, cold start on wake) and paid-tier pricing — REQUIRE CURRENT EXTERNAL
VERIFICATION. Do not act on the numbers below without checking render.com/pricing.**

| | A | B |
|---|---|---|
| | **1 Render service**, ≥ 1 GB ⇒ a **paid** tier (512 MB is the free ceiling per current understanding — VERIFY). Historically ~$7+/mo (VERIFY). One deploy, one env set, one health check, one cold start. | **2 Render services.** If both fit 512 MB ⇒ **both free ⇒ $0** (subject to the shared monthly instance-hour budget — VERIFY whether it is per-service or per-account). Two deploys, two cold starts. |
| Verdict | Simplest single deploy — but **leaves the $0 goal**. | Preserves $0 (the whole point of this track), at the cost of a second deploy unit. |

### 2.12 Failure isolation

| | A | B |
|---|---|---|
| | auth shares one JVM with reports/exports. **The Group 6 export-OOM risk would take auth down too — users could not log in during an incident.** A bad deploy of any 1 of 13 services = whole platform down, auth included. Single blast radius. | auth isolated. Aggregate OOM/crash ⇒ **existing sessions keep working** (stateless JWT, 24 h) and **new logins still succeed**. auth crash ⇒ token validation unaffected (stateless), only new logins blocked. |
| Verdict | Weakest point of A — co-locating the "must-stay-up" service with the "most likely to OOM" workload. | **B is materially better.** auth isolation is standard practice. |

### 2.13 Cold starts

| | A | B |
|---|---|---|
| | One cold start (~11–13 s) warms all 13 services. First post-idle request slow, then everything hot. | Two independent cold starts. Worst case (both idle, user logs in): auth cold-start (~7–9 s) then aggregate cold-start (~10 s) on the first data call — but the aggregate warms while the user reads the post-login screen, and auth's start is fast. |
| Verdict | Simpler, single cold path. | Two, each smaller; slightly worse worst-case perceived latency. Roughly a wash. |

### 2.14 Scaling

| | A | B |
|---|---|---|
| | Cannot scale auth (CPU-bound) and reports (memory-bound) independently — scaling = a bigger box for the monolith. Render free = 1 instance (VERIFY); the `SchedulingConfig`/no-ShedLock assumption already depends on exactly 1 instance. | auth scales on CPU, aggregate on memory, independently. At $0 neither scales anyway (1 instance each), but the *path* to scaling is clean. |
| Verdict | Coupled scaling. | Decoupled — better if the product grows. |

### 2.15 Operational complexity

| | A | B |
|---|---|---|
| | **1** unit: one deploy, one log stream, one health check, one env set (`JWT_SECRET`, DB URL, `GOOGLE_CLIENT_ID`, CORS origins, `JAVA_OPTS`). | **2** units: two deploys, two log streams, two health checks, two env sets — with a **shared `JWT_SECRET`** (drift ⇒ all tokens rejected) and a **shared CORS allowlist** (drift ⇒ browser breakage). Frontend routing split. |
| Verdict | **A is simpler to operate.** | More moving parts; each individually simple, but two things to keep in sync. |

### 2.16 Cost implications

**REQUIRES CURRENT RENDER PRICING VERIFICATION.** Under the current common understanding:

| Scenario | A | B |
|---|---|---|
| Strict $0 | **Not possible** (≥ 1 GB is paid) | **Possible** — aggregate on free 512 MB (with Group 6 export mitigations + accepted WARNING) + auth on free 512 MB |
| Budget allows one paid ≥ 1 GB box | 1 × ≥1 GB paid (everything comfortable). ~1 unit of cost. | 1 × ≥1 GB paid aggregate + 1 × free auth. **Same ~1 unit of cost**, and the paid box now has *more* free headroom because auth isn't in it. |

**Key point:** if you are paying for ≥ 1 GB anyway, A and B cost the same — and B still gives
better isolation while leaving the paid box roomier. A is only cheaper-to-operate, never
cheaper-to-run. **$0 is achievable only under B.**

### 2.17 Security implications

| | A | B |
|---|---|---|
| | BCrypt hashing, token *minting*, the `refresh_tokens` table, `JWT_SECRET` in memory, and Google ID-token verification all run in the **same address space** as reports, PDF/Excel export, and 11 other services. A memory-safety bug or logic flaw in any one shares process memory with password material. The `DaoAuthenticationProvider` + `UserDetailsService` username/password path is added into the single shared filter chain. The principal for `/auth/me` becomes a **mutable JPA entity** (vs the immutable `SpikePrincipal` record) — care needed. `google-api-client` (guava 33, grpc, apache HC4) enlarges the attack surface if Google login is enabled. | auth is a small, single-purpose, **independently patchable/hardenable** service with a minimal TCB for the auth function. Password hashing and secret material isolated. The aggregate keeps its clean model: **stateless JWT validation only — no password handling, no user table, no token minting**. |
| Verdict | Larger, blended trust boundary. | **B is the stronger posture.** Isolating the auth/identity service is a well-established practice. |

Both options share one `JWT_SECRET` (unavoidable — the aggregate must verify what auth signs).
In B it lives in two env stores; in A, one.

### 2.18 Development complexity

| | A | B |
|---|---|---|
| | The **most invasive group yet.** Required work: (1) `@AuthenticationPrincipal User` resolver branch (load `User` by `userId`, handle a non-record principal); (2) wire `DaoAuthenticationProvider` / `AuthenticationManager` / `PasswordEncoder(12)` / `UserDetailsServiceImpl` app-side (config package not scanned); (3) add exactly the 6 `/api/v1/auth/**` public paths to the **one** permit list (first genuinely new public surface — must not accidentally expose `/auth/me` or `/auth/logout`); (4) `authFlyway` + 4 verbatim migration copies; (5) `google-api-client` on the classpath + resolve the **guava 19 vs 33** tension + assess the runtime class-load cost when Google is enabled; (6) `@EnableKafka` + `kafkaEventExecutor` + gate the `@Scheduled` OTP cleanup; (7) `OtpConfig`/`OtpProperties`/`GoogleAuthProperties` + SMS provider wiring; (8) resolve the `auditorAwareImpl` bean-name collision; (9) full security-matrix re-test + a 13-service 512 MB→≥1 GB memory re-measurement. | **Zero aggregate changes.** auth-service is already built, tested (`mvn` + Testcontainers), and has a `Dockerfile`. Group 7 becomes: deploy it to Render, add CORS, add the frontend route, share `JWT_SECRET`. A config/ops task, not an aggregation. Low risk. |
| Verdict | High-risk, touches the shared security chain and JPA config, needs a memory re-baseline. | Low-risk, mostly deployment config. |

---

## 3. Scorecard

| Factor | Favours |
|---|---|
| 1. Memory capacity | **B** (A needs a bigger box just to fit; auth consumes the new headroom) |
| 2. Startup time | ~ tie (B slightly, smaller units) |
| 3. JWT/auth flow | **B** (already works; A adds sensitive wiring) |
| 4. Frontend routing | **A** (one rewrite vs two) |
| 5. CORS | **A** (one config vs two — but both need the config *added*) |
| 6. Internal service auth | tie |
| 7. X-Internal-Auth | tie |
| 8. Database connections | **A** (10 vs ~20; one pool) |
| 9. Flyway | **B** (clean isolation) |
| 10. Kafka-disabled | tie |
| 11. Render deployment | **A** simpler / **B** preserves $0 → **B** (the track's goal) |
| 12. Failure isolation | **B** (strong — auth must stay up) |
| 13. Cold starts | ~ tie |
| 14. Scaling | **B** (decoupled) |
| 15. Operational complexity | **A** (one unit) |
| 16. Cost | **B** ($0 only possible under B; equal cost if paying) |
| 17. Security | **B** (isolated identity service) |
| 18. Development complexity | **B** (near-zero vs the most invasive group) |

**A wins:** frontend routing, CORS config count, DB connection count, operational simplicity (4).
**B wins:** memory, auth-flow risk, Flyway isolation, Render/$0, failure isolation, scaling,
cost, security, dev complexity (9).
**Ties:** 5.

The factors A wins are all **convenience** (one config file instead of two). The factors B wins
include the **hard constraints**: memory does not fit, $0 is lost, the security-critical service
loses its isolation, and it is the riskiest group to build.

---

## 4. RECOMMENDATION

> ### **B — keep auth-service standalone.**

Do **not** embed auth-service in `backend/app`. Deploy it as its own Render service.

**Why B, decisively:**

1. **The 12-service aggregate has no room.** Group 6 already measured it at 94–99 % of 512 MB
   and *required* `MaxRAMPercentage=55` to avoid OOM. auth adds an estimated **+15–25 MiB
   structural** (before any Google class-load or export concurrency), which pushes the peak
   **over 512 MB** — Option A simply cannot run on the free tier, and on a paid ≥1 GB box the
   auth footprint eats headroom that should go to fixing the *actual* Group 6 problem (large
   PDF/Excel export memory).

2. **auth is the wrong service to co-locate.** It is the one service that must stay up during any
   incident, it is CPU-heavy (BCrypt work-factor 12 on 0.1 vCPU), it holds password/secret
   material, and it is the **most invasive** service to aggregate (entity-typed principal,
   `DaoAuthenticationProvider`, social login with a 6.4 MB guava/grpc closure, new public
   endpoints on the shared filter chain).

3. **B is the only path that preserves $0** — the explicit goal of this track. And if the budget
   later moves to a paid ≥1 GB instance, B costs the same as A while keeping auth isolated.

4. **B is near-zero effort and near-zero risk.** auth-service is already built, tested, and has a
   Dockerfile. Group 7 becomes a deployment + routing + CORS task, not an aggregation.

The convenience A offers (one rewrite rule, one CORS block, one deploy) does not outweigh a
capacity constraint that A cannot satisfy on the free tier and a security/reliability regression
for the identity service.

---

## 5. Required outputs

### Minimum recommended memory

| Unit | Minimum | Rationale |
|---|---|---|
| `backend/app` (12 services) | **768 MiB** | Group 6 peak ~505 MiB + safe margin for export concurrency + the non-streaming PDF export. Clears the Group 6 WARNING. If held at **512 MiB**, the Group 6 export mitigations (cap export row count / add a streaming PDF path / limit concurrent exports) are **mandatory**, and the residual OOM risk under abuse is accepted. |
| `auth-service` (standalone) | **512 MiB** | Estimated peak ~300–400 MiB (JPA + Security + web; comparable to farm/customer standalone). Likely runs on **384 MiB** with `MaxRAMPercentage` tuning — **verify with one containerised run before committing to a tier.** |

### Conservative production memory

| Unit | Conservative |
|---|---|
| `backend/app` | **1 GiB** (comfortable for 12 services + concurrent large exports + headroom for a future 13th non-auth service) |
| `auth-service` | **512 MiB** |

### Should 512 MB be retained?

- **`auth-service`: yes.** 512 MB is comfortable; the constraint is not binding here.
- **`backend/app`: only conditionally.** 512 MB is retainable **for now** *only* with the Group 6
  export mitigations applied and the WARNING formally accepted. It is **not recommended for
  production as-is**. Move to 768 MiB–1 GiB when budget allows; that is where the export
  workload becomes safe. Do **not** retain 512 MB *and* add a 13th service.

### Should Group 7 proceed?

**Not as an aggregation.** Proceed with Group 7 re-scoped as **"deploy auth-service standalone +
frontend routing + CORS"**. Do not add auth-service to `backend/app/pom.xml` or its component
scan.

### Exact prerequisites before Group 7 (Option B — recommended)

1. **CORS on the aggregate** — add a `CorsConfigurationSource`/filter to `backend/app` driven by
   `FARM2HOME_FRONTEND_ORIGINS` (mirror the api-gateway's `CorsConfig`: exact origins,
   `allowCredentials=true`, expose `Content-Disposition` for the report downloads). *This is a
   pre-existing gap, needed regardless of Group 7.*
2. **CORS on auth-service** — add the same config to `auth-service` (its `SecurityConfig`
   currently has none — it relied on the gateway). Permit preflight `OPTIONS /api/v1/auth/**`.
3. **Shared `JWT_SECRET`** — set the identical base64 `JWT_SECRET` env var on both Render
   services. Document that rotating it is a coordinated 2-service operation.
4. **auth-service env** — `JWT_SECRET`, `SPRING_DATASOURCE_URL` (Neon, `?currentSchema=auth` or
   `default_schema`), `GOOGLE_CLIENT_ID` (blank ⇒ Google button disabled, fail-closed),
   `SMS_PROVIDER=logging`, disable Eureka/Config-Server (`eureka.client.enabled=false`,
   `spring.cloud.config.enabled=false`) — auth's `application.yml` still assumes them.
5. **auth-service Dockerfile** — add container-aware JVM flags (`-XX:MaxRAMPercentage`,
   `-XX:+UseSerialGC`) matching the aggregate's approach; the current `ENTRYPOINT ["java","-jar","app.jar"]`
   has none.
6. **Kafka** — set `spring.kafka` producer `max.block.ms` low + confirm the `@Async` producers
   fail non-fatally with no broker (auth standalone on Render has none) — mirror the Group 1–6
   producer hardening.
7. **`@Scheduled` OTP cleanup** — confirm it is safe with exactly 1 auth instance (no ShedLock);
   it is (idempotent delete-by-expiry).
8. **Frontend routing** — two Vercel rewrites, `/api/v1/auth/* → auth` **before** `/api/v1/* →
   aggregate`. `VITE_GOOGLE_CLIENT_ID` = the same value as auth's `GOOGLE_CLIENT_ID`.
9. **Neon connection budget** — confirm aggregate pool (10) + auth pool (10, min-idle 2) is
   within the Neon free-tier connection limit — **REQUIRES CURRENT NEON VERIFICATION**; use the
   pooled endpoint if needed.
10. **Measure auth-service standalone** in a 512 MB container (one run) to confirm the ~300–400
    MiB estimate and pick 384 vs 512 MiB.
11. **Apply the Group 6 export mitigations** to `backend/app` before it goes to production on
    512 MB (or size it to 768 MiB–1 GiB and skip).
12. **Render free-tier + pricing verification** — confirm current RAM/CPU/spin-down/instance-hour
    limits and paid-tier prices at render.com **before** finalising instance sizes and the
    cost story in this document.

### If Option A is chosen anyway (not recommended)

All of B's items 1, 3, 6, 10, 12 **plus**: move `backend/app` to ≥ 1 GB (paid); implement the
`@AuthenticationPrincipal User` resolver branch; wire `DaoAuthenticationProvider` /
`AuthenticationManager` / `PasswordEncoder(12)` / `UserDetailsServiceImpl`; add the 6
`/api/v1/auth/**` public paths to the single permit list; `authFlyway` + 4 verbatim migration
copies; resolve the `auditorAwareImpl` bean-name collision; add `google-api-client` and resolve
the guava 19↔33 tension; re-run the full security matrix; **re-measure the 13-service peak in the
chosen ≥1 GB container** (Group 6's numbers do not carry over).

---

## 6. Data-verification flags

The following were **not** verified in this analysis and must be checked against current external
sources before acting:

- **Render free-tier specs** (512 MB RAM, ~0.1 vCPU, spin-down interval, monthly instance-hour
  budget and whether it is per-service or per-account, cold-start behaviour) — render.com/docs.
- **Render paid-tier pricing** for ≥ 1 GB instances — render.com/pricing.
- **Neon free-tier connection limit** and whether the pooled endpoint is required for
  10 + 10 = 20 direct connections — neon.tech/docs.
- **auth-service standalone memory** — estimated ~300–400 MiB from structure; not measured.
- **Option-A 13-service memory peak** — projected ~520–530 MiB on the current config; not
  measured (and cannot be, since it would exceed 512 MB).

All memory figures for `backend/app` (12 services) are the **measured** Group 6 values.

---

*STOP. No implementation, no commit, no push. auth-service was inspected read-only and not
aggregated.*
