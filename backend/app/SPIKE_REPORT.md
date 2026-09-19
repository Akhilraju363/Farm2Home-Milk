# Farm2Home Single-JVM Spike Report

Branch: `spike/render-single-jvm` (created from `main`, **not committed, not pushed**)
Date: 2026-09-06 · Scope: farm-service + production-service + required shared-libs only

---

## 1. Result

**PASS WITH CONDITIONS**

All 22 success criteria were met and live-verified against PostgreSQL. Two conditions, neither a
functional blocker for the spike's question ("can these run in one JVM?"):

1. **Uber-jar packaging is deferred.** farm-service and production-service each apply
   `spring-boot-maven-plugin`, so their published `*.jar` is a Boot *executable* jar (classes under
   `BOOT-INF/classes/`) and cannot be consumed as a plain library. `backend/app` therefore builds,
   tests and runs correctly **in the Maven reactor** (`mvn -pl app -am …`) or from an exploded
   classpath, but `java -jar app.jar` does not work yet. This is a P1-1 concern (make the library
   modules stop self-repackaging), not a runtime feasibility problem.
2. **Render 512 MB container limit: NOT VERIFIED — Docker unavailable** in this environment.
   Memory was measured on the Windows host instead (see §9).

---

## 2. What Was Created

New module **`backend/app/`**:

| File | Purpose |
|---|---|
| `pom.xml` | Spike module. Depends on `farm-service` + `production-service` (transitively brings the 3 shared libs + all starters). Adds `jjwt-{api,impl,jackson}` (gateway not running → JVM validates JWT). Disables the parent JaCoCo `check` for this module only; adds sibling `target/classes` to the surefire classpath. |
| `src/main/java/com/farm2home/app/Application.java` | `@SpringBootApplication` with **narrowed** `scanBasePackages` — only `controller`/`service`/`mapper` of each service + `com.farm2home.app`. Never scans either service's `config` package (would collide). |
| `src/main/java/com/farm2home/app/security/SpikePrincipal.java` | Auth principal established from JWT claims. |
| `src/main/java/com/farm2home/app/security/SpikeJwtAuthenticationFilter.java` | `OncePerRequestFilter`: verifies HMAC signature + expiry with `jwt.secret`, **requires `type=ACCESS`** (rejects REFRESH 401), builds principal from `userId`/`sub`/`roles`, grants **both** `X` and `ROLE_X` authorities per role, strips client-supplied `X-User-*` / `X-Internal-Auth`. |
| `src/main/java/com/farm2home/app/security/SpikeSecurityConfig.java` | The **single** `@EnableWebSecurity`/`@EnableMethodSecurity` + one `SecurityFilterChain`. permitAll for `ApiConstants.PUBLIC_ENDPOINTS_BASE` + `/uploads/**`; everything else authenticated; 401 entry point. |
| `src/main/java/com/farm2home/app/config/SpikeJpaConfig.java` | The **single** `@EnableJpaRepositories` (farm + production + `common.core.audit` packages) + `@EntityScan` + `@EnableJpaAuditing(auditorAwareRef="auditorAwareImpl")`. |
| `src/main/java/com/farm2home/app/config/SpikeAuditorAware.java` | `auditorAwareImpl` bean (replaces both services' excluded `@Component("auditorAwareImpl")`). |
| `src/main/java/com/farm2home/app/config/SpikeFlywayConfig.java` | **Two** `Flyway` beans — `farmFlyway` (`classpath:db/migration/farm`, schema `farm`) and `productionFlyway` (`classpath:db/migration/production`, schema `production`) — plus `EntityManagerFactoryDependsOnPostProcessor` so Hibernate `validate` waits for both. |
| `src/main/resources/application.yml` | `server.port=${PORT:8080}`; env-driven datasource (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`); one Hikari pool (max 10 / min-idle 0); `hibernate.default_schema=farm`; `spring.flyway.enabled=false`; Eureka / discovery / config / loadbalancer all `enabled: false`; `farm2home.observability.config-server-health.enabled=false`; actuator exposes `health,info` only. |
| `src/main/resources/db/migration/farm/V1..V7*.sql` | **Byte-for-byte copies** of `farm-service/src/main/resources/db/migration/V1..V7` (checksums unchanged → validate cleanly against existing history). |
| `src/main/resources/db/migration/production/V1..V2*.sql` | Byte-for-byte copies of `production-service/src/main/resources/db/migration/V1..V2`. |
| `src/test/java/com/farm2home/app/SpikeSingleJvmTests.java` | 10 `@SpringBootTest(RANDOM_PORT)` tests (§10). |
| `SPIKE_REPORT.md` | This report. |

**Migration files copied (not moved) — each service keeps its original so it still runs standalone:**

```
farm-service/.../db/migration/V1__init_farm_schema.sql        → app/.../db/migration/farm/V1__init_farm_schema.sql
farm-service/.../db/migration/V2__create_farms_table.sql      → app/.../db/migration/farm/V2__create_farms_table.sql
farm-service/.../db/migration/V3__create_audit_log.sql        → app/.../db/migration/farm/V3__create_audit_log.sql
farm-service/.../db/migration/V4__add_farm_id_to_cows.sql     → app/.../db/migration/farm/V4__add_farm_id_to_cows.sql
farm-service/.../db/migration/V5__add_farms_created_at_index.sql → app/.../db/migration/farm/V5__...
farm-service/.../db/migration/V6__create_business_settings.sql → app/.../db/migration/farm/V6__...
farm-service/.../db/migration/V7__add_business_address.sql    → app/.../db/migration/farm/V7__...
production-service/.../db/migration/V1__init_production_schema.sql → app/.../db/migration/production/V1__...
production-service/.../db/migration/V2__create_audit_log.sql  → app/.../db/migration/production/V2__...
```

---

## 3. Existing Files Modified

| File | Change | Why |
|---|---|---|
| `backend/pom.xml` | Added `<module>app</module>` (with a "remove before merge" comment). | The reactor must know the spike module. Does **not** touch any service's build or deployment config. |
| `backend/shared-libs/common-observability/.../ObservabilityAutoConfiguration.java` | Added `@ConditionalOnProperty(prefix="farm2home.observability.config-server-health", name="enabled", havingValue="true", matchIfMissing=true)` to the two nested `*ConfigServerHealthConfiguration` classes. | Step 11: without a Config Server the `ConfigServerHealthIndicator` drags `/actuator/health` to DOWN. This is a **property-based, default-on, backward-compatible** opt-out (every existing service leaves it unset and is unaffected). It touches only config-server-health wiring — tracing / startup-logger untouched. This is the **only** shared-code change. |

No business logic, no controller, no entity, no schema semantics, no service pom, no Eureka /
Config Server / Gateway / Kafka code was modified or deleted.

---

## 4. Spring Context

- **Startup result:** SUCCESS. `Started Application in 7.9 s – 17.4 s` across runs (`-Xmx256m`,
  SerialGC, exploded classpath on a busy Windows host; cold vs. warm OS cache).
- **Bean count:** not captured (`/actuator/beans` deliberately not exposed). The single-instance
  facts below are asserted directly by tests instead.
- **Duplicate bean issues:** none. The two services' `config` packages are never scanned, so no
  duplicate `SecurityConfig` / `JpaConfig` / `auditorAwareImpl` / `GatewayHeaderAuthFilter`.
  Startup log has exactly **one** non-standard WARN: `GatewayTrust is NOT enforcing…` — the same
  by-design local/dev message every service logs. The Spring Cloud LoadBalancer WARN flood was
  eliminated via `spring.cloud.loadbalancer.enabled=false`.
- **SecurityFilterChain count:** **1** (test-asserted).
- **DispatcherServlet count:** **1** (test-asserted; log shows one `dispatcherServlet` init).
- **EntityManagerFactory count:** **1** (test-asserted).
- **Flyway bean count:** **2** — `farmFlyway`, `productionFlyway` (test-asserted).
- **Repositories:** `farmRepository`, `cowRepository`, `businessSettingsRepository`,
  `milkProductionRepository` all present; exactly **1** `AuditLogRepository` (test-asserted).

---

## 5. Database

- **Connection:** PASS — `jdbc:postgresql://localhost:5432/farm2home` (PostgreSQL 16.14), single
  Hikari pool `spike-hikari`, `maximum-pool-size=10`, `minimum-idle=0`.
- **farm schema:** PASS. `farm.flyway_schema_history` = **7 versioned migrations applied**
  (`init farm schema` … `add business address`). `farm.cows`, `farm.farms`,
  `farm.business_settings` present. Flyway: *"Successfully validated 8 migrations … Schema farm is
  up to date. No migration necessary."*
- **production schema:** PASS. `production.flyway_schema_history` = **2 versioned migrations
  applied** (`init production schema`, `create audit log`). `production.milk_production` present.
  Flyway: *"Successfully validated 3 migrations … Schema production is up to date."*
- **Migration count:** 7 (farm) + 2 (production) = 9, in **two separate histories**.
- **Collisions / duplicate-migration / checksum / cross-schema errors:** **none.** Each Flyway
  instance scans only its own location; the byte-identical copies matched existing checksums, so
  nothing re-ran.
- **Schemas intact:** row counts identical before and after every run —
  `farm.farms=4`, `farm.business_settings=1`, `farm.audit_log=16`, `production.milk_production=0`,
  histories `8`/`3` rows (7+baseline / 2+baseline). Test farm created during verification was
  hard-deleted; its 2 audit rows removed.
- **Known limitation:** the shared `common-core` `AuditLog` entity has no `@Table(schema=)`, so
  with `hibernate.default_schema=farm` **all** audit rows in the combined app (farm *and*
  production) route to `farm.audit_log`. `production.audit_log` stays intact but unused. Real fix
  (schema-per-audit or a routing datasource) belongs to full aggregation.

---

## 6. Security

Live-verified with JWTs minted against the dev key (`jwt.secret`), plus 5 automated tests.

| Check | Result |
|---|---|
| **JWT signature/expiry** validated (jjwt, HMAC, base64 key — same as auth-service/gateway) | PASS |
| **Public endpoint** `/actuator/health` without JWT → `200 {"status":"UP"}` | PASS |
| **Protected** `GET /api/v1/farm` **no JWT** → `401` | PASS |
| **Invalid JWT** (`abc.def.ghi`) → `401` | PASS |
| **Refresh-type JWT** → `401` (`type != ACCESS` rejected) | PASS |
| **Valid ACCESS JWT** → `GET /api/v1/farm` `200`, `GET /api/v1/productions` `200` (same process) | PASS |
| **Role authorization — farm** (`hasAnyRole`): `POST /api/v1/farm` valid body as `CUSTOMER` → `403`; as `FARM_MANAGER` → `201`; `DELETE` as `SUPER_ADMIN` → `200` | PASS |
| **Role authorization — production** (`hasAnyAuthority`): `GET /api/v1/productions/summary/today` as `CUSTOMER` → `403`; as `DELIVERY_MANAGER` → `200` | PASS |
| **Forged-header** `X-User-Id`/`X-User-Roles` sent by client | Ignored — no `GatewayHeaderAuthFilter` is loaded and `SpikeJwtAuthenticationFilter` strips gateway-owned headers before the chain. |

Key design point: farm controllers use `hasAnyRole(...)` (needs `ROLE_` prefix) and production
controllers use `hasAnyAuthority(...)` (needs the bare name). The one JWT filter grants **both**
`FARM_MANAGER` **and** `ROLE_FARM_MANAGER`, so each service's existing rules work unchanged. No
authorization rule was invented or weakened. (The only refinement: a 401 entry point instead of
Spring's default 403 for unauthenticated requests, matching the api-gateway.)

---

## 7. Infrastructure Independence

| | Result | Evidence |
|---|---|---|
| **Eureka** | PASS | `eureka.client.enabled=false` + `spring.cloud.discovery.enabled=false`. No Netflix/DiscoveryClient/registration lines in the startup log. App fully serves traffic. |
| **Config Server** | PASS | No `spring.config.import`. `ConfigServerHealthIndicator` disabled via the new property. `/actuator/health` = UP with nothing on `localhost:8888`. |
| **Kafka** | PASS | farm-service, production-service and the shared libs have **no** `spring-kafka` / `KafkaTemplate` dependency (verified). No broker contacted; no `farm2home.kafka.*` property needed. App starts with no broker. |

---

## 8. Endpoints

| | Result |
|---|---|
| **Farm** — `GET /api/v1/farm` (simplest existing farm endpoint) → `200`, JSON `ApiResponse` envelope with a real paged DB result; `POST /api/v1/farm` created + `DELETE` removed a farm row (DB write confirmed). | PASS |
| **Production** — `GET /api/v1/productions` (simplest existing production endpoint) → `200`, JSON paged DB result, **served by the same process/port as the farm endpoint**. | PASS |
| **Health** — `GET /actuator/health` → `200 {"status":"UP","groups":["liveness","readiness"]}`; `/actuator/health/readiness` → `UP`. `/actuator/env` **not exposed**. | PASS |

---

## 9. Performance

Measured on the **Windows host** (not a container): `java -Xmx256m -Xms64m -XX:+UseSerialGC`,
exploded classpath, local Postgres.

| Metric | Value |
|---|---|
| Startup time (`Started Application in …`) | **7.9 s (warm)** – **18.1 s (cold)** |
| Application readiness (`startupTimeMs`) | 7 978 – 17 538 ms |
| Flyway startup time | farm ≈ 0.09 s + production ≈ 0.015 s validate (both "up to date", no migrate) |
| Hibernate init | ≈ 3–4 s (bootstrap + `validate` of farm+production+audit entities) |
| JVM heap used (post-ready, `jcmd GC.heap_info`) | **≈ 64 MB** (young ≈ 11 MB + tenured ≈ 53 MB) |
| Metaspace used | ≈ 92 MB |
| Native memory committed (`jcmd VM.native_memory`) | **≈ 283 MB** |
| Process RSS (`WorkingSet64`) | **≈ 340–355 MB** |
| Docker 512 MB run | **NOT VERIFIED — Docker unavailable** |

**READY assessment:** RSS ~350 MB and committed ~283 MB are comfortably under 512 MB on this
host, which is encouraging — but a real cgroup-limited 512 MB container run was not possible.
Do **not** claim Render compatibility until measured in a 512 MB container.

---

## 10. Tests

`mvn -o -pl app clean test` (also passes under `mvn -pl app -am verify`):

```
Tests run: 10   Passed: 10   Failed: 0   Errors: 0   Skipped: 0
```

| Test | Proves |
|---|---|
| `oneServletDispatcherOneSecurityChainOneEntityManagerFactory` | 1 `DispatcherServlet`, 1 `SecurityFilterChain`, 1 `EntityManagerFactory` |
| `twoFlywayInstancesOneForEachSchema` | exactly `farmFlyway` + `productionFlyway` |
| `farmProductionAndAuditRepositoriesAllDiscoveredExactlyOnce` | farm + production + single audit repository beans |
| `servletServletStackNotReactive` | servlet stack, one `ServletWebServerFactory` |
| `farmSchemaHasItsOwnCompleteMigrationHistory` | 7 farm migrations, farm tables exist, no production migration in farm history |
| `productionSchemaHasItsOwnCompleteMigrationHistory` | 2 production migrations, isolated from farm history |
| `healthIsPublicAndUpWithoutEurekaConfigServerOrKafka` | `/actuator/health` = 200 UP |
| `protectedFarmEndpointRejectsMissingAndBadAndRefreshTokens` | 401 for missing / malformed / refresh token |
| `validAccessTokenReachesBothFarmAndProductionEndpointsInOneProcess` | farm + production 200 in one JVM |
| `roleRestrictionsStillEffectiveAcrossBothServices` | `hasAnyRole` (farm) and `hasAnyAuthority` (production) both enforced |

No existing test was modified or deleted. `git diff --check`: clean.

---

## 11. Remaining Problems

Concrete items only:

1. **Library modules self-repackage** → no working `java -jar app.jar`; spike builds/tests only
   in the reactor. Fix in P1-1: farm-service/production-service must publish plain library jars
   (or the aggregator restructures).
2. **Shared `AuditLog` has no schema** → combined app funnels all audit rows to `farm.audit_log`.
   Needs a deliberate design decision in P1-1 (per-schema audit, routing datasource, or accept
   consolidation).
3. **512 MB container memory: unmeasured** (Docker unavailable). Must be verified before any
   Render claim.
4. `spring-cloud-starter-netflix-eureka-client` still on the classpath (transitive from the two
   services) even though disabled — it pulls LoadBalancer/Commons weight. P1-1 can decide whether
   to `<exclude>` it.

None of these prevent the spike's conclusion.

---

## 12. Architecture Decision

**SPIKE SUCCESSFUL.**

One Spring Boot servlet JVM started, ran both services' Flyway migrations into isolated schemas,
connected to PostgreSQL, served farm **and** production endpoints from the same process/port,
enforced JWT authentication and both services' distinct authorization styles, and reported
`/actuator/health` UP with no Eureka, no Config Server and no Kafka — bound to `${PORT}`.

---

## 13. Recommendation

**Proceed to P1-1 full application aggregation only after reviewing this spike.**

Carry forward the four solved mechanics (narrowed component scan, dual-authority JWT filter,
single composed `@EnableJpaRepositories`, one-Flyway-per-schema with copied migrations) and
resolve the four items in §11 — especially the library-jar packaging change and a container-limited
memory measurement — as explicit early tasks in P1-1.

---

*Working tree left intact on branch `spike/render-single-jvm`. Nothing committed or pushed.*
