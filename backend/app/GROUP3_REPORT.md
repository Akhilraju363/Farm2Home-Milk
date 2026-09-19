# Farm2Home Group 3 Aggregation Report

Branch: `spike/render-single-jvm` · 2026-09-06 · **Nothing committed or pushed.**
Scope: subscription-service + order-service only. Groups 4–7 not touched.

---

## 1. Status

**PASS.**

`backend/app` now embeds **6 business services** — farm, production, customer, inventory,
subscription, order — in one servlet JVM. 26/26 aggregation tests green. Verified live (jar +
512 MB Docker container). Groups 1 & 2 regression intact.

---

## 2. Services Added

- **subscription-service** — subscription lifecycle (create/update/pause/resume/cancel), reports,
  3 nightly `@Scheduled` jobs, producer-only Kafka.
- **order-service** — manual + cart-checkout orders, daily subscription-order generation, 2 Kafka
  consumers + 1 producer, synchronous REST calls to customer / inventory / production.

`commerce-core` **does not exist** — there is no such module (searched every `pom.xml`). The
"commerce ownership model" is the shared in-code pattern (`UserPrincipal.isAdmin()` +
service-layer `principal.userId()` filtering); nothing to integrate.

---

## 3. Packaging

Unchanged pattern. `subscription-service` / `order-service` added to `app/pom.xml` as
`<classifier>lib</classifier>` deps. Verified:

| | subscription-service | order-service |
|---|---|---|
| main `*.jar` still executable (`Start-Class`) | ✅ | ✅ |
| thin `*-lib.jar` produced | ✅ (72 KB) | ✅ (152 KB) |
| `-lib.jar` contains `application*.yml` / `db/migration/**` / `logback-spring.xml` / bootstrap | **0** | **0** |

`app/target/app.jar` — executable, bundles all six service `-lib.jars`, exactly **one**
`application.yml`. Standalone service packaging and every per-service Dockerfile untouched.

---

## 4. Component Scanning

`Application.java` — extended `scanBasePackages` (same `FullyQualifiedAnnotationBeanNameGenerator`,
no new naming strategy):

| Service | Packages scanned |
|---|---|
| subscription | `.controller` `.service` `.mapper` `.kafka` (no `client` package) |
| order | `.controller` `.service` `.mapper` `.client` `.kafka` |

`config` packages **never** scanned. New excluded classes and how each is replaced app-side:

| Excluded (order/subscription) | Handled by |
|---|---|
| `config.SecurityConfig` | app `SpikeSecurityConfig` (one chain) — **no new public paths** (both are `PUBLIC_ENDPOINTS_BASE` only) |
| `config.JpaConfig` (`@EnableJpaRepositories`) | app `SpikeJpaConfig` |
| `config.AuditorAwareImpl` (`@Component("auditorAwareImpl")`) | app `SpikeAuditorAware` |
| `config.WebClientConfig` (`loadBalancedWebClientBuilder`) — order only | app `LoadBalancerClientConfig` |
| `config.GatewayHeaderAuthFilter`, `config.UserPrincipal`, `config.OpenApiConfig` | dropped / resolver (§6) |
| **`config.MilkPriceProperties`** (`@ConfigurationProperties(prefix="milk")`, `@Component`) — order only | **NEW** app `OrderPricingConfig` — `@EnableConfigurationProperties(MilkPriceProperties.class)` + `milk.prices` block copied verbatim into app `application.yml` |
| `kafka.KafkaConfig` (bean `kafkaConfig` ×4 now) | scanned; coexist via FQN bean-name generator |

The 4 `kafka.KafkaConfig` classes all register as `com.farm2home.<svc>.kafka.KafkaConfig` — no
`kafkaConfig` collision.

---

## 5. Security

**SecurityFilterChain count: 1** (test-asserted). `@EnableMethodSecurity` once. No new public
endpoints.

### Authorization matrix — subscription (verbatim from `SubscriptionController`)

| Endpoint | Rule |
|---|---|
| `GET /subscriptions/summary`, `/reports`, `/analytics/subscription-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| `POST /subscriptions`, `GET /subscriptions`, `/search`, `/{id}`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/pause`, `/{id}/resume`, `GET /export` | authenticated + service-layer ownership: `principal.isAdmin() ? … : principal.userId()` (`isAdmin` = FARM_MANAGER∪SUPER_ADMIN) |

### Authorization matrix — order (verbatim from `OrderController` / `CartController`)

| Endpoint | Rule |
|---|---|
| `POST /orders/generate` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| `GET /orders/summary`, `/reports` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| `POST /orders`, `/checkout`, `GET /orders`, `/search`, `/{id}`, `/subscription/{id}`, `PATCH /{id}/status`, `DELETE /{id}`, `GET /export`, all `/cart/**` | authenticated + ownership (`isAdmin` = FARM_MANAGER∪DELIVERY_MANAGER∪SUPER_ADMIN) |

All `hasAnyAuthority(...)` (bare) — no `hasRole`. The JWT filter grants both `X` and `ROLE_X`, so
unchanged.

### Verified (live + tests)

| Check | subscription | order |
|---|---|---|
| no JWT → 401 | ✅ | ✅ (`/orders`, `/cart`) |
| bad JWT → 401 | ✅ | ✅ |
| refresh JWT (`type != ACCESS`) → 401 | ✅ | ✅ |
| forged `X-User-Roles: SUPER_ADMIN`, no JWT → 401 | ✅ | ✅ |
| `/summary` CUSTOMER → 403 | ✅ | ✅ |
| `/summary` DELIVERY_MANAGER / FARM_MANAGER → 200 | ✅ | ✅ |
| `/analytics/*-trend?granularity=DAILY` CUSTOMER → 403 (method security beats missing-param 400) | ✅ | n/a |
| `POST /orders/generate` CUSTOMER → 403; FARM_MANAGER → not 403 | n/a | ✅ |
| plain list CUSTOMER → 200 (ownership-filtered) | ✅ | ✅ |

Authorization not weakened; no method-level rule replaced by an endpoint permit.

---

## 6. @AuthenticationPrincipal Handling

`ServiceUserPrincipalArgumentResolver` — **unchanged**. subscription (~10 sites in
`SubscriptionController`) and order (~15 sites: `CartController` 5, `OrderController` 10) each
inject `@AuthenticationPrincipal <svc>.config.UserPrincipal`, both records with the identical
`(UUID userId, String mobile, Set<String> roles)` canonical constructor. The resolver reflects
each from the JWT `SpikePrincipal` and both services' `principal.isAdmin()` /
`principal.userId()` ownership logic runs correctly. Verified live: `GET /subscriptions`,
`GET /orders`, `GET /cart` all 200 for a CUSTOMER token (each depends on the injected principal).

No controller or service modified.

---

## 7. JPA

- **EntityManagerFactory count: 1** (test-asserted). No second datasource. Same
  `spike-hikari` pool (max 10 / min-idle 0).
- One `@EnableJpaRepositories` extended with `subscription.domain.repository`,
  `order.domain.repository`.
- Repository beans discovered exactly once (test-asserted): `subscriptionRepository`,
  `orderRepository`, `cartRepository`, `orderItemRepository`, `subscriptionSnapshotRepository`
  (+ the prior 9 + one `AuditLogRepository`).
- No duplicate repositories, no overriding (`spring.main.allow-bean-definition-overriding`
  **not** set).

---

## 8. Flyway

`spring.flyway.enabled=false`; two new beans:

| Bean | Schema | Location | Versioned migrations applied |
|---|---|---|---|
| `subscriptionFlyway` | `subscription` | `classpath:db/migration/subscription` | 2 |
| `orderFlyway` | `order` (reserved word — Flyway/Hibernate quote it; entities use `@Table(schema="`order`")`) | `classpath:db/migration/order` | 6 |

8 SQL files copied **verbatim** (subscription V1–V2, order V1–V6). The shared helper now also
sets `.baselineOnMigrate(true)` — matching subscription/order's own yml; a no-op for every
schema here (all already have a `flyway_schema_history`).

Verified: migrations apply, no version collisions, no checksum errors, **no cross-schema
contamination** — `"order".flyway_schema_history` contains only the 6 order descriptions,
`subscription` only its 2. All 6 histories: `8 / 3 / 8 / 8 / 3 / 7` rows (versioned + baseline).

**Audit-log decision UNCHANGED** — `hibernate.default_schema=farm`, so all 6 services' audit
rows land in `farm.audit_log` (`service_name="farm2home-spike"`); each `<schema>.audit_log`
stays intact but unused. The dedicated `audit` schema remains a deferred Phase-10 decision.

---

## 9. REST / lb:// Resolution

`LoadBalancerClientConfig` in-process rewrite — **unchanged**. Eureka + Spring Cloud
LoadBalancer stay off; no `SimpleDiscoveryClient`.

| Caller → `lb://` target | Classification | Verified |
|---|---|---|
| order → `lb://customer-service` (`/customers/{id}/delivery-availability`) | **EMBEDDED** (Group 2) | ✅ in-process loopback |
| order → `lb://inventory-service` (`getProduct`, `decrement-stock`) | **EMBEDDED** (Group 2) | ✅ |
| order → `lb://production-service` (`getDailySummary`) | **EMBEDDED** (Group 1) | ✅ |
| subscription | **no outbound clients** | n/a |

**All of order's inter-service dependencies are now embedded.** order does not call
payment-service or delivery-service directly (it only *reacts* to delivery events via the
stopped Kafka consumer), so there are **no NOT-EMBEDDED dependencies** for Group 3.

Verified live:

- **order → customer → farm (2-hop nested loopback):** `POST /api/v1/orders` with a milk item →
  order calls `lb://customer-service/.../delivery-availability` → customer calls
  `lb://farm-service/.../business-settings` → both 200 → order created, `totalAmount = 130`
  (2 L TONED @ ₹65 from `MilkPriceProperties`). Log shows two `ReactorNetty` loopback requests
  from `127.0.0.1` with the forwarded `Authorization` + `X-Correlation-ID`.
- **order → inventory synchronous stock decrement (Step 8 special attention):**
  `POST /api/v1/orders` with a product item → `inventoryServiceClient.decrementStock(...).block()`
  → loopback `POST /api/v1/inventory/products/{id}/decrement-stock` → 200; product stock went
  **100 → 97** (exactly −3) *before* the order response returned. **Synchronous behaviour
  preserved — not converted to async.** (Test data restored afterward.)

`RequestHeaderForwarder` (common-web, modified in Group 2 to also forward `Authorization`) is
what authenticates every loopback hop.

---

## 10. Kafka Behavior

- **Broker dependency: none.** Startup never touches a broker.
- **Consumers — all stopped** (test-asserted via `KafkaListenerEndpointRegistry`):
  - order's `kafkaListenerContainerFactory` (custom bean, same name as Boot's default — Boot
    backs off) drives **both** `DeliveryEventConsumer` and `SubscriptionEventConsumer`
    (`@KafkaListener` with no `containerFactory=` → default). `KafkaDisabledConfig`'s
    `BeanPostProcessor` forces `autoStartup=false` on it (the global
    `spring.kafka.listener.auto-startup=false` does **not** reach custom factories — confirmed
    in Group 2). Startup log: *"Kafka listener container factory 'kafkaListenerContainerFactory'
    set to autoStartup=false"*.
  - plus customer's `customerKafkaListenerContainerFactory` (Group 2).
- **Producers — lazy, non-fatal:** subscription's `subscriptionKafkaTemplate` and order's
  `orderKafkaTemplate` are created lazily; both services' own `KafkaConfig` already sets
  `max.block.ms=3000` and both producer classes wrap `send()` in try/catch, so a publish on a
  mutating request fails fast and is logged — never rolls back the DB transaction, never blocks
  startup.
- **`spring.kafka.consumer.group-id`** added to app yml (`farm2home-app`) — order's `KafkaConfig`
  and `@KafkaListener(groupId = "${…}")` read it even though the listeners never start.
- **Stray `@Bean ObjectMapper kafkaObjectMapper()`** in order's `KafkaConfig` — see §20.1.

**Event functionality unavailable in this deployment** (until the in-process event bridge
phase): subscription lifecycle events → order's `subscription_snapshots` sync + notification
alerts; order-created events → notification/invoice; delivery-status → order status sync. All
**synchronous** REST paths (incl. order→inventory stock decrement) are unaffected.

---

## 11. Scheduler Behavior

- One app-level `@EnableScheduling` (`SchedulingConfig`), one `TaskScheduler`,
  `spring.task.scheduling.pool.size=8`. No duplicate scheduling (no service `*Application` is
  scanned).
- Embedded `@Scheduled` jobs and their cron:

| Job | Cron | Nature |
|---|---|---|
| inventory `alertLowStockItems` | `0 0 8 * * *` | audit write + (degraded) Kafka publish |
| subscription `expireEndedSubscriptions` | `0 0 1 * * *` | bulk UPDATE, idempotent |
| subscription `autoResumePausedSubscriptions` | `0 5 1 * * *` | UPDATE, idempotent |
| subscription `checkUpcomingRenewals` | `0 10 1 * * *` | **read-only** (log) |
| order `DailyOrderGenerationService.generateForTomorrow` | `0 0 23 * * *` | **writes `"order".orders`** — idempotent (skips a subscription+date that already has an order); reads only `subscription_snapshots` (fed by the stopped consumer → currently 0 rows) |

- **Prod:** scheduler ON by default (`farm2home.scheduling.enabled=true`; Render runs exactly
  **1 instance**; no ShedLock this phase — documented).
- **Tests:** `@SpringBootTest(properties = "farm2home.scheduling.enabled=false")` →
  `SchedulingConfig` conditionally absent → **zero `@Scheduled` tasks registered**
  (test-asserted). A test run straddling 23:00 cannot fire the daily-order-generation job.
  This is a test-only guard, not a production behaviour change — it is documented, not silent.
- Verified: no scheduled job logged any output during startup or the test run; `"order".orders`
  count unchanged.

---

## 12. commerce-core Integration

**N/A — no such module exists.** No duplicate beans, no second context, no config classes, no
second datasource, no Kafka infra, no scheduling, no security config from any "commerce-core".
Nothing was component-scanned for it.

---

## 13. Database Validation

Six schemas, each with its own `flyway_schema_history`:

| Schema | Versioned migrations | vs. original service | Representative tables verified |
|---|---|---|---|
| farm | 7 | 7 | cows, farms |
| production | 2 | 2 | milk_production |
| customer | 7 | 7 | customers, consent_records |
| inventory | 7 | 7 | products, reviews |
| **subscription** | **2** | **2** | subscriptions |
| **order** | **6** | **6** | orders, carts, subscription_snapshots |

- No version collisions, no checksum errors, no cross-schema contamination (SQL-verified).
- **Safe write/read validation:**
  - order — `POST /api/v1/orders` (milk item) created an order + item, then `POST /api/v1/orders`
    (product item) decremented `inventory.products.stock_quantity` 100→97. Both **deleted /
    restored** afterward.
  - subscription — `GET /api/v1/subscriptions` read the 6 existing rows via the ownership filter
    (no write needed; create requires a customer profile + address that a synthetic test JWT
    lacks).
- All verification test data removed; baseline restored (`"order".orders`=8, `"order".carts`=2,
  `subscription.subscriptions`=6, `farm.audit_log`=16, `farm.farms`=4). Baseline data not
  modified.

---

## 14. Endpoint Validation (actual endpoints, actual results)

| Service | Endpoint | Auth | HTTP |
|---|---|---|---|
| — | `GET /actuator/health` | none | 200 UP |
| farm | `GET /api/v1/farm` | FARM_MANAGER | 200 |
| production | `GET /api/v1/productions` | FARM_MANAGER | 200 |
| customer | `GET /api/v1/customers` | SUPER_ADMIN / CUSTOMER | 200 / 403 |
| inventory | `GET /api/v1/inventory/products` | CUSTOMER | 200 |
| subscription | `GET /api/v1/subscriptions` | none / CUSTOMER | 401 / 200 |
| subscription | `GET /api/v1/subscriptions/summary` | CUSTOMER / DELIVERY_MANAGER | 403 / 200 |
| order | `GET /api/v1/orders` | CUSTOMER | 200 (`@AuthenticationPrincipal`) |
| order | `GET /api/v1/cart` | CUSTOMER | 200 (`@AuthenticationPrincipal`) |
| order | `GET /api/v1/orders/summary` | CUSTOMER / FARM_MANAGER | 403 / 200 |
| order | `POST /api/v1/orders/generate` | CUSTOMER / FARM_MANAGER | 403 / 200 |
| order | `POST /api/v1/orders` (milk item) | SUPER_ADMIN | 201 + 2-hop loopback |
| order | `POST /api/v1/orders` (product item) | SUPER_ADMIN | 201 + synchronous stock −3 |

Same results verified inside the 512 MB Docker container.

---

## 15. Group 1 Regression

- **Farm: PASS** — `GET /api/v1/farm` 200, `POST` role restriction 403, migrations intact
- **Production: PASS** — `GET /api/v1/productions` 200, `summary/today` role restriction 403/200

## 16. Group 2 Regression

- **Customer: PASS** — list authz 401/403/200, `/me/consents` 200, `delivery-availability`
  in-process loopback 200
- **Inventory: PASS** — products 200, `/summary` 403/200, `/reviews/my` 200/403,
  `product-categories` create 403/201

All prior test assertions retained in the 26-test suite.

---

## 17. Test Results

`mvn -pl app verify` (app module; deps pre-installed): **BUILD SUCCESS**

```
Tests run: 26   Passed: 26   Failed: 0   Errors: 0   Skipped: 0
```

(20 in Group 2 → 26 now: +6 for subscription/order schema histories, role restrictions, scheduler
guard, ObjectMapper/MilkPriceProperties bean checks, all-six-services reachability, order-creation
loopback.)

`mvn -pl app -am verify` (full reactor): **BUILD FAILURE at common-core** —
`EndToEndWorkflowIntegrationTest` → *"Could not find a valid Docker environment"*.
**Pre-existing and unrelated** — a Testcontainers test in `common-core` (not `app`); Testcontainers
cannot attach on this Windows host even though the Docker CLI works (it built + ran the app image
fine). Not caused by this aggregation. The JaCoCo coverage gate is untouched (disabled for the
`app` module only).

---

## 18. Docker Results

- Docker available: **YES** (29.6.2)
- `backend/app/Dockerfile` — unchanged from Group 2 (`eclipse-temurin:21-jre-alpine`,
  `COPY target/app.jar`, `java -jar app.jar`, `${PORT}`, `-XX:MaxRAMPercentage=70`).
- Image built: **190 MB content / 530 MB disk**.
- **512 MB run (`--memory=512m --memory-swap=512m`): PASS**
  - all 6 services 200 in-container; 401/403 correct
  - **OOMKilled: false**, container stayed Running, 0 OOM / GC-overhead markers in logs
  - Flyway: all 6 schemas applied

---

## 19. Memory Results

Measured via `docker stats` inside the 512 MB container:

| | Group 2 (4 svcs) | **Group 3 (6 svcs)** |
|---|---|---|
| Image content size | 190 MB | 190 MB |
| Startup (`Started Application in`) | 18.98 s | **20.84 s** |
| Ready (wall clock incl. container start) | ~22.5 s | **~23.8 s** |
| Steady memory | ~338 MiB / 512 (66 %) | **~348 MiB / 512 (68 %)** |
| Peak under light load (40+ mixed reqs) | not measured | **~362 MiB / 512 (71 %)** |
| OOMKilled | false | **false** |

**+10 MiB steady, +1.9 s startup** for subscription + order. ~150 MiB of headroom remains.
The architecture stays viable under 512 MB for 6 services. (This is not a Render-readiness
claim.)

---

## 20. New Issues / Discoveries

1. **order's `@Bean ObjectMapper kafkaObjectMapper()`** (in `order.kafka.KafkaConfig`) makes
   Boot's `@Primary jacksonObjectMapper` back off (`@ConditionalOnMissingBean`), leaving a bare
   `new ObjectMapper()+JavaTimeModule` as the only `ObjectMapper` — which Spring MVC would then
   use for **all 6 services'** request/response JSON, dropping every Boot customisation
   (`FAIL_ON_UNKNOWN_PROPERTIES=false`, `Jdk8Module`, `spring.jackson.*`, …). **Fix (app-only):**
   `AppJacksonConfig` — a `@Primary ObjectMapper` built exactly as Boot builds its own
   (`Jackson2ObjectMapperBuilder`). Test-asserted that MVC's `ObjectMapper` is the `@Primary`
   one and not `kafkaObjectMapper`. No order-service change.
2. **`MilkPriceProperties`** lives in the never-scanned `order.config` package but is required by
   `OrderServiceImpl` / `DailyOrderGenerationService`. Registered via app `OrderPricingConfig`
   (`@EnableConfigurationProperties`) + `milk.prices` copied verbatim into app yml. Verified: a
   2 L TONED order priced at ₹130 (₹65/L).
3. **`order` is a SQL reserved word.** Handled: entities already use `@Table(schema="`order`")`
   (backtick-quoted); the app's Flyway helper and test SQL quote it (`"order"`). No migration SQL
   changed.
4. **Custom Kafka listener factory named `kafkaListenerContainerFactory`** (== Boot's default
   name) — Boot backs off, order's wins, `KafkaDisabledConfig` still catches it
   (`AbstractKafkaListenerContainerFactory`). Confirms the Group-2 BPP approach was the right
   call, not the global property.
5. **`order.carts` get-or-create** — `GET /api/v1/cart` auto-creates a cart row for the caller.
   The synthetic test-JWT userId therefore leaves a cart row; the test suite / manual checks
   clean it up. Not a bug — documented so future groups' cleanup accounts for it.

---

## 21. Known Limitations

- 3-hop nested in-process loopback (order → customer → farm) consumes 3 servlet threads per such
  request. Fine at normal load; under heavy concurrency of order-creation this could pressure the
  Tomcat thread pool. A single-JVM-loopback tradeoff; revisit if load-testing shows it.
- All Kafka event flows for order/subscription are dormant (no broker) — see §10.
- `DailyOrderGenerationService` reads `subscription_snapshots`, which is fed only by the stopped
  Kafka consumer → in this deployment it processes only pre-existing snapshots. Full behaviour
  returns with the in-process event bridge.
- Aggregate audit `service_name` is uniform ("farm2home-spike") — forensic granularity by that
  column alone is lost (still distinguishable by `entity_type` / `request_uri`). Fixed by the
  deferred Phase-10 `audit` schema.
- Business scheduler is OFF in tests (property) — see §11.

---

## 22. Architecture Impact

| | |
|---|---|
| Group 1 (farm, production) | **PASS** |
| Group 2 (customer, inventory) | **PASS** |
| Group 3 (subscription, order) | **PASS** |
| Full aggregation (13 services) | **NOT DONE** — 6/13 embedded |
| Render | **NOT READY** (no deploy; 512 MB memory constraint verified for the current 6-service footprint at ~348 MiB steady / ~362 MiB peak) |

One `SecurityFilterChain`, one `EntityManagerFactory`, one `DispatcherServlet`, one
`TaskScheduler`, one Hikari pool, six Flyway instances, no Eureka, no Config Server, no Kafka
broker, no second datasource — all held.

---

## 23. Exact Files Changed

**New (this group), under `backend/app/`:**

- `src/main/java/com/farm2home/app/config/AppJacksonConfig.java` — `@Primary ObjectMapper`
- `src/main/java/com/farm2home/app/config/OrderPricingConfig.java` — `MilkPriceProperties` registration
- `src/main/resources/db/migration/subscription/V1–V2` (2 files, verbatim copies)
- `src/main/resources/db/migration/order/V1–V6` (6 files, verbatim copies)

**Modified (this group):**

- `backend/app/pom.xml` — +2 `<classifier>lib</classifier>` deps
- `backend/app/src/main/java/com/farm2home/app/Application.java` — +9 scan packages, Group-3 doc
- `backend/app/src/main/java/com/farm2home/app/config/SpikeJpaConfig.java` — +2 repo/entity packages
- `backend/app/src/main/java/com/farm2home/app/config/SpikeFlywayConfig.java` — +`subscriptionFlyway`, +`orderFlyway`, `.baselineOnMigrate(true)`
- `backend/app/src/main/java/com/farm2home/app/config/SchedulingConfig.java` — +`@ConditionalOnProperty(farm2home.scheduling.enabled)`
- `backend/app/src/main/resources/application.yml` — `spring.kafka.consumer.group-id`, `spring.task.scheduling.pool.size`, `milk.prices`, `farm2home.scheduling.enabled`
- `backend/app/src/test/java/com/farm2home/app/SpikeSingleJvmTests.java` — 20 → 26 tests

**No change** to any service module, migration SQL, `common-*` (the Group-2
`RequestHeaderForwarder` change stands), `backend/pom.xml`, or any Dockerfile other than the
app's own (unchanged since Group 2).

---

## 24. Recommended Next Step

**Stop and review before implementing Group 4** (payment-service + delivery-service).

Group 4 is a separate cycle and the first with a **NOT-EMBEDDED → EMBEDDED transition that other
services already call**: order's (stopped) `DeliveryEventConsumer` and customer's
`DeliveryRouteSelectionServiceImpl` (`lb://delivery-service`) currently degrade gracefully;
adding delivery makes those loopbacks live. payment-service adds `PaymentGatewayConfig` /
`PaymentAsyncConfig`, the legacy-callback toggle, and the wallet-first-use edge case. Do not
begin until this Group 3 change is reviewed.

---

*Working tree left uncommitted on `spike/render-single-jvm`. Full P1-1 diff: `backend/pom.xml`
(Phase 2), `backend/app/` (Groups 1–3),
`common-observability/ObservabilityAutoConfiguration.java` (spike),
`common-web/RequestHeaderForwarder.java` (Group 2). The ` M` on
`common-web/.../RequestHeaderForwarderTest.java` predates this work.*
