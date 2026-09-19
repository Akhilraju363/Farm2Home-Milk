# Farm2Home Full Single-JVM Aggregation Report (P1-1)

Branch: `spike/render-single-jvm` · Date: 2026-09-06 · **Nothing committed or pushed.**

> **Scope note (read first).** P1-1 asked for a full 13-service production aggregation across
> 26 phases, each group gated by "compile → start context → test → STOP if it fails". That is a
> multi-session migration. This session completed and **verified** the mandated first gate —
> **Phase 2 (packaging)** — and performed the complete code-level investigation the later phases
> depend on (Phases 4/5/8/9/10/15/16). The consolidated runtime (Groups 2–7) is **designed but
> not yet implemented or tested**, so this report does not claim it works. Docker is unavailable,
> so Render compatibility cannot be claimed this session regardless of code state.

---

## 1. Overall Status

**PASS WITH CONDITIONS — for Phase 2 only.**
**Full aggregation: NOT DONE (design complete, implementation pending).**
**Render readiness: NOT READY.**

---

## 2. Modules Aggregated

| Group | Modules | State |
|---|---|---|
| 1 | farm, production | **Aggregating & verified** (spike + Phase-2 rebuild: `java -jar app.jar` serves both, health UP) |
| 2 | customer, inventory | investigated, not wired |
| 3 | subscription, order | investigated, not wired |
| 4 | payment, delivery | investigated, not wired |
| 5 | notification, invoice | investigated, not wired |
| 6 | dashboard, reports | investigated, not wired |
| 7 | auth | investigated, not wired |

`backend/app` currently embeds **farm-service + production-service** (thin `-lib` jars) and boots
as one executable JAR.

---

## 3. Modules Excluded (and why they stay excluded)

- **api-gateway** — reactive (Spring Cloud Gateway / WebFlux). Cannot coexist with a servlet
  MVC runtime. Its cross-cutting duties (JWT auth, CORS, gateway-owned header stripping, internal
  trust) are re-homed into `app` as servlet equivalents; its `lb://` routing layer is not needed
  once all controllers are in one JVM. Module untouched, still deployable for PATH B.
- **discovery-service (Eureka)** — not embedded; `eureka.client.enabled=false`. Module untouched.
- **config-server** — not embedded; app runs entirely from local/env config; the
  `ConfigServerHealthIndicator` is disabled by property. Module untouched.

All Kafka code, all three infra modules, and every microservice module remain in the repo and
independently buildable/deployable. The existing per-service Dockerfiles are unchanged.

---

## 4. Packaging  — ✅ DONE & VERIFIED (Phase 2)

**Problem:** every business service applies `spring-boot-maven-plugin`, so its main `*.jar` is a
Boot *executable* (fat) jar (classes under `BOOT-INF/classes/`). Unusable as a library — `app`'s
`@ComponentScan` / `@EnableJpaRepositories` see nothing.

**Solution (least-invasive, Spring-reference approach):** one addition to `backend/pom.xml`
`<build><plugins>` — a `maven-jar-plugin` `library-jar` execution producing a **second, thin,
classified `*-lib.jar`** per module (`skipIfEmpty`, and it excludes each service's
`application*.yml` / `logback-spring.xml` / `bootstrap*.yml` / `db/migration/**` so no service
runtime config leaks into the aggregate). `backend/app` consumes each service as
`<classifier>lib</classifier>`.

**Nothing else changed:** the main `*.jar` is still fattened by `spring-boot:repackage`
afterwards, so every service stays independently executable and **all 15 Dockerfiles + the
start-all-services script are untouched**.

| `mvn clean package` produces | Verified |
|---|---|
| `farm-service/target/farm-service-1.0.0-SNAPSHOT.jar` — executable (`Start-Class: FarmServiceApplication`) | ✅ |
| `farm-service/target/farm-service-1.0.0-SNAPSHOT-lib.jar` — thin (82 files, no `BOOT-INF`, no yml, no migrations) | ✅ |
| `app/target/app.jar` — executable (`Start-Class: com.farm2home.app.Application`), bundles the `-lib` jars | ✅ |
| `java -jar app/target/app.jar` — boots farm+production, `/actuator/health` 200, both endpoints 200 | ✅ |
| `app/target/app.jar` contains exactly **one** `application.yml` (app's own) | ✅ |

The **final Render artifact = `app/target/app.jar`.**

---

## 5. Spring Context (Group 1 — measured; Groups 2–7 — projected)

Measured, `java -jar app.jar` (farm + production), local Postgres, `-Xmx256m`:

- Startup: **12.8 s** · Started, Tomcat on `${PORT}`, health UP
- SecurityFilterChain: **1** · DispatcherServlet: **1** · EntityManagerFactory: **1** · Flyway: **2**
- Duplicate beans / duplicate repositories: **none**
- Bean count: not instrumented (`/actuator/beans` not exposed)

**Projected blockers when Groups 2–7 are added (from code inspection):**

| Conflict | Count | Consolidation |
|---|---|---|
| `@Configuration SecurityConfig` (`securityFilterChain`/`filterChain` bean) | 13 | 1 app-level `AggregateSecurityConfig` |
| `@Component GatewayHeaderAuthFilter` (bean `gatewayHeaderAuthFilter`) | 12 | dropped — replaced by 1 servlet JWT filter |
| `@Component("auditorAwareImpl") AuditorAwareImpl` | 11 | 1 app-level bean named `auditorAwareImpl` |
| `@Configuration JpaConfig` (`@EnableJpaRepositories` ×N — do not compose) | 11 | 1 app-level `@EnableJpaRepositories` over all repo packages |
| `@Configuration WebClientConfig` (`@LoadBalanced WebClient.Builder loadBalancedWebClientBuilder`) | 9 | 1 app-level `@LoadBalanced` builder + static instances |
| `@Configuration @EnableKafka KafkaConfig` (bean `kafkaConfig`) | 8 | see §8 (Kafka) |
| `@Configuration OpenApiConfig` | 13 | 1 app-level, or drop |
| `record UserPrincipal` (per-service, `@AuthenticationPrincipal` target) | 12 | see §6 (principal resolver) |
| `@EnableScheduling` on `*Application` | 5 | 1 app-level `@EnableScheduling` |

**Component-scan strategy (Phase 4):** never `com.farm2home`. Scan `com.farm2home.app` +, per
embedded service, exactly: `…​.controller`, `…​.service`, `…​.mapper`, `…​.client`, `…​.kafka`
(consumers/producers only). **Never** `…​.config`. Use
`FullyQualifiedAnnotationBeanNameGenerator` so same-simple-named `@Configuration`/`@Component`
classes across modules (e.g. every `KafkaConfig`) don't collide on bean name. Repositories/
entities are reached via the one app-level `@EnableJpaRepositories`/`@EntityScan`, not by scan.

**Excluded config classes (documented, Phase 4):** for every embedded service —
`config.SecurityConfig`, `config.JpaConfig`, `config.AuditorAwareImpl`, `config.WebClientConfig`,
`config.GatewayHeaderAuthFilter`, `config.UserPrincipal`, `config.OpenApiConfig`; plus service-
specific: auth `GoogleAuthConfig`/`OtpConfig`/`JwtAuthenticationFilter`/`JwtSecretGuard`,
payment `PaymentAsyncConfig`/`PaymentGatewayConfig`, order `MilkPriceProperties`,
notification `EmailConfig`; and `kafka.KafkaConfig` per service (replaced app-side).

---

## 6. Security — consolidated authorization matrix (read from code, nothing inferred)

**Every service SecurityConfig is structurally identical:** `csrf disable`, `STATELESS`,
`permitAll(PUBLIC)`, `anyRequest().authenticated()`, add a header/JWT filter, `@EnableMethodSecurity`.
Real per-endpoint rules live in controller `@PreAuthorize`. Endpoints with **no** `@PreAuthorize`
= "any authenticated caller", with ownership narrowing done in the service layer via
`principal.isAdmin()` / `principal.userId()`.

### 6a. Consolidated public (permitAll) set — union of all 13

| Path | From |
|---|---|
| `/actuator/health`, `/actuator/health/**`, `/actuator/info` | all (`ApiConstants.PUBLIC_ENDPOINTS_BASE`) |
| `/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | all |
| `/uploads/**` | customer, inventory, farm |
| `POST /api/v1/auth/register`, `/auth/login`, `/auth/send-otp`, `/auth/verify-otp`, `/auth/refresh-token`, `/auth/google` | auth |
| `POST /api/v1/payments/webhook` | payment (Razorpay HMAC-verified in `handleWebhook`) |
| `POST /api/v1/data-rights-requests/submit` | customer (DPDP intake; method-specific) |

Everything else → `authenticated()`.

### 6b. Role-restricted endpoints (`@PreAuthorize`) — verbatim from source

| Service | Endpoint(s) | Rule |
|---|---|---|
| **auth** | `POST /auth/logout` | authenticated |
| | `GET /auth/me` | authenticated · `@AuthenticationPrincipal User` (JPA entity — special, see §6d) |
| **customer** | `GET /customers` | `hasAnyAuthority(SUPER_ADMIN, DELIVERY_MANAGER)` |
| | `DELETE /customers/{id}` | `hasAnyAuthority(SUPER_ADMIN)` |
| | `GET /customers/summary`, `/reports`, `/analytics/growth-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| | `GET /customers/search` | `hasAnyAuthority(SUPER_ADMIN, DELIVERY_MANAGER, FARM_MANAGER)` |
| | `GET /customers/export` | `hasAnyAuthority(SUPER_ADMIN, DELIVERY_MANAGER)` |
| | `GET/PUT /customers/{id}`, `/{id}/addresses*`, `/{id}/delivery-availability` | authenticated + service-layer ownership (`principal.isAdmin()` bypass; `UserPrincipal.isAdmin` = SUPER_ADMIN∪DELIVERY_MANAGER∪FARM_MANAGER here) |
| | `POST /customers/{id}/profile-image`, `/me/addresses`, `/me/consents*` | authenticated + ownership |
| | `GET /data-rights-requests`, `PATCH /data-rights-requests/{id}/status` | `hasAnyAuthority(SUPER_ADMIN)` |
| | `/location-master/**` (whole controller) | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER)` |
| | `/locations/**` (states/districts/cities lookup) | authenticated |
| **subscription** | `GET /subscriptions/summary`, `/reports`, `/analytics/subscription-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| | all other `/subscriptions/**` (CRUD, pause/resume, search, export) | authenticated + service-layer ownership (`isAdmin` = FARM_MANAGER∪SUPER_ADMIN) |
| **order** | `POST /orders/generate` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| | `GET /orders/summary`, `/reports` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| | `/cart/**`, other `/orders/**`, `/orders/export` | authenticated + ownership (`isAdmin` = FARM_MANAGER∪DELIVERY_MANAGER∪SUPER_ADMIN) |
| **payment** | `POST /payments/callback` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER)` **AND** disabled unless `farm2home.payment.legacy-callback-enabled=true` |
| | `POST /payments/{id}/refund` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| | `GET /payments/summary`, `/reports`, `/export`, `/analytics/revenue-trend`, `/analytics/payment-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| | `POST /payments`, `/{id}/verify`, `GET /payments`, `/{id}`, `/order/{orderId}*`, `/wallets/**` | authenticated + ownership (`isAdmin` = FARM_MANAGER∪SUPER_ADMIN) |
| **delivery** | `POST /delivery/assignments`, `POST/GET/PUT /delivery/partners*`, `POST/PUT/PATCH/DELETE /delivery/routes*` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| | `GET /delivery/assignments/summary`, `/reports`, `/analytics/performance-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| | assignment list/detail/status, `/assignments/{id}/location*` | authenticated + ownership (`isAdmin` = FARM_MANAGER∪SUPER_ADMIN∪DELIVERY_MANAGER; `isDeliveryPartner()` used for partner-scoped views) |
| **farm** | `POST/PUT/DELETE /farm`, `/farm/cows*`, `/farm/**/vaccinations*`, `/farm/**/health*`, `/farm/business-settings` (writes) | `hasAnyRole(FARM_MANAGER, SUPER_ADMIN)` |
| | `GET /farm`, `/farm/search`, `/farm/{id}`, `/farm/business-settings` | authenticated |
| **production** | `GET /productions/summary/today`, `/reports`, `/analytics/production-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| | `DELETE /productions/{id}` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| | `POST/GET/PUT /productions*` | `isAuthenticated()` |
| **inventory** | `POST/PUT/DELETE /inventory`, `/inventory/products*`, `/inventory/product-categories*` (writes, image) | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| | `GET /inventory/summary`, `/transactions/reports`, `/analytics/consumption-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| | `POST/PUT/DELETE /reviews*`, `GET /reviews/my` | `hasAnyAuthority(CUSTOMER)` (delete also allows FARM_MANAGER/SUPER_ADMIN) |
| | all other `GET /inventory/**`, `/inventory/*/transactions` | `isAuthenticated()` |
| **notification** | `GET /notifications/logs`, `/logs/{id}` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| | `GET /notifications/summary` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| | `GET /notifications/me`, `PATCH /{id}/read`, `/read-all` | authenticated + ownership |
| **invoice** | `POST /invoices/generate/{orderId}`, `GET /invoices` | `hasAnyRole(FARM_MANAGER, SUPER_ADMIN)` |
| | `GET /invoices/me`, `/{id}`, `/order/{orderId}`, `/{id}/pdf` | authenticated + ownership (`isAdmin` = FARM_MANAGER∪SUPER_ADMIN) |
| **dashboard** | `GET /dashboard/summary` | `hasAnyAuthority(SUPER_ADMIN, …)` (class/method) |
| **reports** | whole `/reports/**` controller | class-level `@PreAuthorize("hasAnyAuthority(SUPER_ADMIN, …)")` |

**Style split confirmed:** farm + invoice use `hasAnyRole(...)` (needs `ROLE_` prefix); everyone
else uses `hasAnyAuthority(...)` (bare). The JWT filter (§7) grants **both** `X` and `ROLE_X` per
role — proven in the spike — so both styles work under one chain with no rule change.

### 6c. Consolidation plan (Phase 5) — one `SecurityFilterChain`

`csrf disable` · `STATELESS` · `permitAll(§6a union)` · `POST /api/v1/data-rights-requests/submit`
method-specific permitAll · `anyRequest().authenticated()` · `@EnableMethodSecurity` (so all the
`@PreAuthorize` above keep enforcing) · CORS from `FARM2HOME_FRONTEND_ORIGINS` · 401 entry point
· add the JWT filter before `UsernamePasswordAuthenticationFilter`. The 13 service SecurityConfig
classes stay in their modules, unscanned.

### 6d. `@AuthenticationPrincipal` — the real risk (needs an adapter, no business change)

8 services inject `@AuthenticationPrincipal <svc>.config.UserPrincipal` (customer, subscription,
order, payment, delivery, inventory, invoice, notification) — **~60 call sites**, all for
ownership checks. All 12 `UserPrincipal` records have the **identical** canonical constructor
`(UUID userId, String mobile, Set<String> roles)`; only `isAdmin()` differs.

**Plan:** an app-level `HandlerMethodArgumentResolver` that resolves any parameter whose type is
`com.farm2home.*.config.UserPrincipal` by reflectively invoking that record's
`(UUID,String,Set)` constructor from the JWT-derived identity. ~40 lines, registered ahead of
Spring's own resolver. No controller/service edit. **Must be covered by the authorization-matrix
test suite (Phase 20).**

`auth`'s `GET /auth/me` takes `@AuthenticationPrincipal User` (a JPA entity) — Group 7 only;
handled by a dedicated resolver branch that loads the `User` by the JWT `userId`.

---

## 7. JWT Filter (Phase 6) — reuse spike, unchanged design

The spike's `SpikeJwtAuthenticationFilter` already does exactly what P1-1 requires: HMAC verify,
expiry, `type == ACCESS` required (REFRESH → 401), identity from claims, strips
`X-User-*`/`X-Internal-Auth`, grants both bare and `ROLE_`-prefixed authorities. It will be
renamed (`app.security.JwtAuthenticationFilter`) and kept. `FARM2HOME_GATEWAY_INTERNAL_SECRET`
required in the `render` profile via a `JwtSecretGuard`-style `@Profile` startup check.

---

## 8. Infrastructure

| | Status | Notes |
|---|---|---|
| **Eureka** | ✅ (Group 1) | `eureka.client.enabled=false`, `spring.cloud.discovery.enabled=false`. Verified no registration attempt. |
| **Config Server** | ✅ (Group 1) | `ConfigServerHealthIndicator` disabled via new `farm2home.observability.config-server-health.enabled=false` property (the one shared-code change — a default-on `@ConditionalOnProperty`). Health UP with nothing on :8888. |
| **Kafka** | ⚠ ANALYZED, not solved | No broker needed for Group 1 (farm/production have no Kafka). Groups 2–5, 7 do. |

### Kafka dependency analysis (Phase 15) — per service

- `farm2home.kafka.enabled` **does not exist anywhere** — the "where supported" caveat resolves
  to "nowhere". Cannot rely on it.
- Each Kafka service has `com.farm2home.<svc>.kafka.KafkaConfig` = `@Configuration @EnableKafka`,
  `@Value("${spring.kafka.bootstrap-servers}")`, defining **uniquely-named** `ProducerFactory` /
  `KafkaTemplate` / `ConsumerFactory` / `ConcurrentKafkaListenerContainerFactory` beans
  (`customerConsumerFactory`, `inventoryProducerFactory`, …) — **no bean-name collision between
  services**, but the `KafkaConfig` **class** collides on bean name `kafkaConfig` → fixed by the
  FQN bean-name generator (§5).
- **What fails at startup vs. runtime:**
  - `DefaultKafkaProducerFactory` / `KafkaTemplate` beans — created lazily, **do not connect at
    startup**. First `.send()` on a mutating request blocks up to `max.block.ms` (default 60 s)
    if some producers lack a timeout/try-catch (known — see the module-status memory). Startup
    is unaffected.
  - `@KafkaListener` containers (customer, order consumers) — with `autoStartup=true` they start
    a background poll thread that logs connection-refused on a loop. **Does not fail startup**
    (confirmed pattern across the project), but noisy.
- **Plan:** set `spring.kafka.listener.auto-startup=false` (consumers dormant) and keep
  `spring.kafka.bootstrap-servers` set to an unreachable default (producers stay lazy). Add
  per-service `try/catch` + `max.block.ms` only where a producer send is on a synchronous
  request path and currently unguarded (payment/order/subscription — minimal, tested changes).
  The in-process event bridge is a **later phase** (not this one).
- **Per-service producer/consumer inventory:** auth (producer: OTP events), customer
  (consumer: CustomerEvent), subscription (producer), order (producer + 2 consumers:
  Delivery/Subscription), payment (producer), delivery (producer + consumer), inventory
  (producer), notification (consumers only — its whole reason to exist).
  **notification-service is consumer-only** → with listeners off it will start but process
  nothing; its REST endpoints (`/notifications/me`, `/logs`) still work.

### Inter-service REST / `lb://` (Phase 8)

9 services declare `@LoadBalanced WebClient.Builder` and call `lb://<service>` via
`client/*ServiceClient` components:

| Caller | Calls |
|---|---|
| customer | `lb://farm-service`, `lb://delivery-service` |
| order | `lb://customer-service`, `lb://inventory-service`, `lb://production-service` |
| inventory | `lb://customer-service`, `lb://order-service` |
| dashboard | (BFF) order/customer/payment/delivery/subscription/production/inventory/notification |
| reports | (BFF) all report-producing services |
| payment, delivery, invoice, notification | 1–2 peers each |

**Plan:** keep loopback REST for the first aggregation. Re-enable `spring-cloud-loadbalancer`,
one app-level `@LoadBalanced WebClient.Builder`, and static resolution:

```yaml
spring.cloud.discovery.client.simple.instances:
  farm-service:      [{ uri: "http://localhost:${PORT:8080}" }]
  customer-service:  [{ uri: "http://localhost:${PORT:8080}" }]
  # …one entry per embedded service name, all pointing at this JVM
eureka.client.enabled: false
```

Every `lb://x` then resolves to this process. No external URLs. If any single client cannot be
resolved this way (e.g. a call that needs a header the loopback won't carry), that one call is
investigated in isolation — callers are **not** rewritten to direct method calls in this phase.

---

## 9. Endpoints — representative per service (all served by one process once aggregated)

farm `GET /api/v1/farm` · production `GET /api/v1/productions` · customer `GET /api/v1/customers/{id}` ·
inventory `GET /api/v1/inventory/products` · subscription `GET /api/v1/subscriptions` ·
order `GET /api/v1/orders` · payment `GET /api/v1/wallets/me` · delivery `GET /api/v1/delivery/assignments` ·
notification `GET /api/v1/notifications/me` · invoice `GET /api/v1/invoices/me` ·
dashboard `GET /api/v1/dashboard/summary` · reports `GET /api/v1/reports/sales` ·
auth `POST /api/v1/auth/login`. **Group 1 (farm, production) verified 200; rest pending wiring.**

---

## 10. Scheduling (Phase 16)

`@EnableScheduling` on 5 `*Application` classes (auth, inventory, order, payment, subscription) —
**not** scanned in `app`, so a single app-level `@EnableScheduling` + `spring.task.scheduling.pool.size=8`.

| Job | Service | Notes for single-instance |
|---|---|---|
| `OtpService` cleanup | auth | expired-OTP purge — idempotent |
| `InventoryItemServiceImpl` low-stock / reorder check | inventory | read + event emit |
| `DailyOrderGenerationService` | order | **generates daily orders from active subscriptions** — MUST run exactly once/day; safe at 1 instance |
| `PaymentReconciliationJob` | payment | reconciles PENDING payments against gateway |
| `SubscriptionServiceImpl` (renewal/expiry) | subscription | state transitions |

**Render must stay at exactly 1 instance.** No ShedLock this phase (documented constraint). All 5
jobs coexist on an 8-thread pool; none contends for the same rows across services.

---

## 11. Tests

| | Count |
|---|---|
| Passed | **10** (`SpikeSingleJvmTests` — Group 1, re-verified after the Phase-2 packaging change, standalone `mvn -pl app test`) |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 |

Pre-existing, **not caused by this work**: `common-core` `EndToEndWorkflowIntegrationTest` errors
with "Could not find a valid Docker environment" (Testcontainers, Docker unavailable) — so a
full-reactor `mvn -pl app -am test` stops in `common-core`. Run app tests standalone or filter
that class. Reported separately per Phase 21; the coverage gate is **not** touched.

**Not yet written:** the Phase-20 application-level suite (context, JWT, the full §6b
authorization matrix, repository discovery, per-schema Flyway isolation, health without
infra, scheduler registration, Kafka-disabled startup). These are blocked on Groups 2–7 wiring.

---

## 12. Docker

- Docker available: **NO** (`docker info` → "docker DOWN")
- Image build: **NOT ATTEMPTED**
- 512 MB run: **NOT VERIFIED**

A `Dockerfile` for `backend/app` (`eclipse-temurin:21-jre-alpine`, `COPY target/app.jar app.jar`,
`ENTRYPOINT java -jar app.jar`, `EXPOSE ${PORT}`) is trivial and drafted in the plan but not
committed, because it cannot be built or memory-tested here.

---

## 13. Memory

Measured on the **Windows host** (not a container), `java -jar app.jar`, Group 1 only, `-Xmx256m`:

| | Value |
|---|---|
| Startup | 12.8 s |
| Heap used (post-ready) | ~64 MB |
| RSS | ~340 MB |
| Container 512 MB | **NOT MEASURED — Docker unavailable** |

Group 1 is 2 of 13 services. **No projection is made for the full app** — memory must be measured
in a real 512 MB container once all groups are wired. Per the instruction: NOT VERIFIED.

---

## 14. Remaining Blockers (concrete)

1. **Groups 2–7 not wired** — component scan, 1 `SecurityConfig`, 1 `@EnableJpaRepositories`
   over all repo packages, per-schema Flyway (11 schemas + `audit`), the `UserPrincipal`
   argument resolver, the `@LoadBalanced` builder + static instances, `@EnableScheduling`.
2. **`@AuthenticationPrincipal` adapter** must be implemented and matrix-tested before any of
   Groups 2/3/4/5 can serve ownership-checked endpoints (they 500 without it).
3. **Kafka producers on synchronous request paths** (payment/order/subscription) need
   `max.block.ms` + try/catch so a mutating request doesn't hang 60 s with no broker.
4. **`auth` `GET /auth/me`** needs the `User`-entity principal branch (Group 7).
5. **Docker unavailable** → Render memory compatibility unverifiable this session.
6. **Flyway migration copies** — 68 SQL files across 11 schemas must be copied verbatim into
   `app/src/main/resources/db/migration/<schema>/` (spike proved the pattern for 2).

---

## 15. Render Readiness

**NOT READY.** Phase 2 (packaging) is production-ready. The aggregated runtime for all 13
services is designed but unbuilt/untested, and Render memory fit is unmeasured.

---

## 16. Required Next Step

**Implement Group 2 (customer + inventory):** add the two `-lib` deps; extend the component scan
to their `controller/service/mapper/client/kafka` packages with the FQN bean-name generator;
stand up the consolidated `AggregateSecurityConfig` + `AggregateJpaConfig` (all repo packages for
Groups 1–2) + the `UserPrincipal` `HandlerMethodArgumentResolver` + the `@LoadBalanced` builder
with static `localhost:${PORT}` instances; copy customer's 7 + inventory's 7 migrations into
`db/migration/{customer,inventory}/` and add their Flyway beans; set
`spring.kafka.listener.auto-startup=false`. Then: `mvn -pl app -am test-compile`, start the
context, and run endpoint + authorization-matrix tests for customer & inventory. **Stop and
diagnose if it fails before touching Group 3.**

---

*Working tree left uncommitted on `spike/render-single-jvm`. `git status` shows exactly three
P1-1 changes: `backend/pom.xml` (library-jar execution), `backend/app/` (module), and
`common-observability/ObservabilityAutoConfiguration.java` (config-server-health property, from
the spike).*
