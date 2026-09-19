# Farm2Home Group 4 Aggregation Report

Branch: `spike/render-single-jvm` · 2026-09-06 · **Nothing committed or pushed.**
Scope: payment-service + delivery-service only. Groups 5–7 not touched.

Every item is classified **PASS / PASS WITH CONDITIONS / FAIL / DEFERRED**.

---

## 1. Status — **PASS**

`backend/app` now embeds **8 business services** — farm, production, customer, inventory,
subscription, order, payment, delivery — in one servlet JVM. 32/32 aggregation tests green.
Verified live (jar + 512 MB Docker container). Groups 1–3 regression intact. Every `lb://`
call among the 8 embedded services now resolves as an in-process loopback (verified:
payment→order, delivery→order, order→customer→farm, order→inventory).

**`commerce-core` does not exist** — no such module (searched every `pom.xml`; `shared-libs/`
holds only `common-*`). The "commerce ownership model" is the in-code
`principal.isAdmin()`/`principal.userId()` pattern; nothing to integrate. **DEFERRED → N/A.**

---

## 2. Services Added — **PASS**

- **payment-service** — payment initiation/verify/refund/webhook, wallet (balance/top-up/txns),
  gateway-provider abstraction (mock default), 5-min reconciliation `@Scheduled` job,
  producer-only Kafka.
- **delivery-service** — assignments (manual + event-driven), partners, routes, live locations,
  1 Kafka consumer + 1 producer, no scheduled jobs.

---

## 3. Packaging — **PASS**

Established thin-`-lib` pattern. Both added to `app/pom.xml` as `<classifier>lib</classifier>`.

| | payment-service | delivery-service |
|---|---|---|
| main `*.jar` still executable (`Start-Class`) | ✅ | ✅ |
| thin `*-lib.jar` produced | ✅ | ✅ |
| `-lib.jar` contains `application*.yml` / `db/migration/**` / logback / bootstrap | **0** | **0** |

`app/target/app.jar` — executable, bundles all 8 service `-lib.jars`, exactly one
`application.yml`. Standalone service packaging + every per-service Dockerfile untouched.

---

## 4. Component Scanning — **PASS**

`Application.java` — same `FullyQualifiedAnnotationBeanNameGenerator`, extended:

| Service | Scanned |
|---|---|
| payment | `.controller` `.service` `.mapper` `.client` `.kafka` `.scheduler` |
| delivery | `.controller` `.service` `.mapper` `.client` `.kafka` |

**Never scanned** — every `config` package, plus (payment-specific):

| Excluded | Why / handled by |
|---|---|
| `config.SecurityConfig` | app `SpikeSecurityConfig` (one chain) |
| `config.JpaConfig` | app `SpikeJpaConfig` |
| `config.AuditorAwareImpl` | app `SpikeAuditorAware` |
| `config.WebClientConfig` | app `LoadBalancerClientConfig` |
| `config.GatewayHeaderAuthFilter` / `UserPrincipal` / `OpenApiConfig` | dropped / resolver |
| `kafka.KafkaConfig` (bean `kafkaConfig` ×6 now) | scanned; coexist via FQN generator |
| `payment.config.PaymentGatewayConfig` | **NEW app `PaymentGatewayAppConfig`** (§10) |
| `payment.config.PaymentAsyncConfig` (`paymentEventExecutor`) | **not needed** — see below |
| `payment.event.*` (`PaymentEventListener`) | **not scanned** — it only does a (dead) Kafka publish via `@Async("paymentEventExecutor")`; `PaymentServiceImpl` publishes through `ApplicationEventPublisher`, so skipping the listener needs no async executor. Test-asserted `paymentEventExecutor` bean is absent. |
| `payment.gateway.*` (`MockPaymentGatewayProvider`, …) | not `@Component`s — `@Bean`-wired by `PaymentGatewayAppConfig` |

---

## 5. Security — **PASS**

**SecurityFilterChain count: 1** (test-asserted). One `@EnableWebSecurity`/`@EnableMethodSecurity`.

**New public endpoint:** `POST /api/v1/payments/webhook` (from payment's own SecurityConfig —
its payload HMAC signature is verified in the handler). `POST /api/v1/payments/callback` is
**NOT** public — it stays admin-only (`hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER)`) **and**
returns 404 unless `farm2home.payment.legacy-callback-enabled=true` (which stays `false`).

### Authorization matrix — payment (verbatim from `PaymentController` / `WalletController`)

| Endpoint | Rule |
|---|---|
| `POST /payments`, `/payments/{id}/verify`, `GET /payments`, `/payments/{id}`, `/payments/order/{orderId}*` | authenticated + service-layer ownership (`PaymentServiceImpl.resolvePayment(id, customerId, isAdmin)`; `payment.config.UserPrincipal.isAdmin()` = **FARM_MANAGER ∪ SUPER_ADMIN**) |
| `POST /payments/callback` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER)` + legacy toggle (default → 404) |
| `POST /payments/{id}/refund` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| `POST /payments/webhook` | **public** (HMAC-verified in handler) |
| `GET /payments/summary`, `/reports`, `/export`, `/analytics/revenue-trend`, `/analytics/payment-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| `GET /wallets/me`, `POST /wallets/topup`, `GET /wallets/transactions` | authenticated + ownership via principal |

### Authorization matrix — delivery

| Endpoint | Rule |
|---|---|
| `DeliveryAssignmentController` `POST` (manualAssign) | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| `GET /delivery/assignments`, `/search`, `/{id}`, `/order/{orderId}`, `PATCH /{id}/status`, `POST /{id}/delay` | authenticated + ownership (`isAdmin` = FARM_MANAGER∪SUPER_ADMIN∪DELIVERY_MANAGER; non-admin resolved to own `DeliveryPartner` → **404 if not a partner**) |
| `GET /delivery/assignments/summary`, `/reports`, `/analytics/performance-trend` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| `DeliveryLocationController` (all) | authenticated + ownership via principal |
| `DeliveryPartnerController` `POST`/`GET`/`PUT`, `DeliveryRouteController` `POST`/`PUT`/`PATCH`/`DELETE` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` — **NOT DELIVERY_MANAGER** |
| `DeliveryRouteController` `GET` list/search/{id}, `DeliveryPartnerController` `GET /{id}` | authenticated |

### Verified (live + tests)

no JWT → 401 · bad JWT → 401 · refresh JWT → 401 · forged `X-User-*` no JWT → 401 ·
`payments/summary` CUSTOMER 403 / DELIVERY_MANAGER 200 · `payments/callback` CUSTOMER 403,
FARM_MANAGER **404** (legacy off) · `payments/webhook` no JWT → reaches handler (public) ·
`payments/{id}/refund` CUSTOMER 403 · `delivery/assignments/summary` CUSTOMER 403 /
DELIVERY_MANAGER 200 · `POST /delivery/partners` CUSTOMER **and** DELIVERY_MANAGER 403,
FARM_MANAGER 201 · `GET /delivery/routes` CUSTOMER 200.

**No authorization weakened; no method-level rule replaced by an endpoint permit.**

---

## 6. @AuthenticationPrincipal — **PASS**

`ServiceUserPrincipalArgumentResolver` — **unchanged**. payment (`PaymentController` 4,
`WalletController` 3) and delivery (`DeliveryAssignmentController` 6, `DeliveryLocationController`
3) each inject `@AuthenticationPrincipal <svc>.config.UserPrincipal`, both records with the
identical `(UUID, String, Set)` canonical constructor. Verified live: `GET /payments` (own-scoped
list), `GET /wallets/me`, `GET /delivery/assignments` (a synthetic CUSTOMER → **404 by the
endpoint's own "not an admin, no DeliveryPartner profile" rule** — proves the resolver produced a
usable principal, not a 500). No controller/service modified.

---

## 7. JPA — **PASS**

- **EntityManagerFactory count: 1**. No second datasource. Same `spike-hikari` pool.
- One `@EnableJpaRepositories` extended with `payment.domain.repository`,
  `delivery.domain.repository`.
- Repository beans discovered once (test-asserted): `paymentRepository`, `walletRepository`,
  `walletTransactionRepository`, `deliveryAssignmentRepository`, `deliveryPartnerRepository`,
  `deliveryRouteRepository`, `deliveryLocationRepository` (+ prior 14 + one `AuditLogRepository`).
- No duplicate repositories, no overriding (`allow-bean-definition-overriding` **not** set).
- Entities: payment `@Table(schema="payment")`, delivery `@Table(schema="delivery")` — all
  qualified; the single `hibernate.default_schema=farm` only affects the shared unqualified
  `AuditLog`.

---

## 8. Flyway — **PASS**

`spring.flyway.enabled=false`; two new beans:

| Bean | Schema | Location | Migrations applied |
|---|---|---|---|
| `paymentFlyway` | `payment` | `classpath:db/migration/payment` | **4** (V1–V4) |
| `deliveryFlyway` | `delivery` | `classpath:db/migration/delivery` | **8** (V1–V8) |

12 SQL files copied **verbatim** (checksums unchanged). Version numbers **do** overlap
numerically with other services (every schema has `V1__`, `V2__`, …) — the per-schema
locations isolate them; that is the whole mechanism. Verified by SQL: `payment.flyway_schema_history`
contains only the 4 payment descriptions, `delivery` only its 8 — **no cross-schema
contamination, no collisions, no checksum errors**. All 8 histories:
`8 / 3 / 8 / 8 / 3 / 7 / 5 / 9` rows (versioned + baseline).

**Audit-log decision UNCHANGED** — all 8 services' audit rows land in `farm.audit_log`
(`hibernate.default_schema=farm`, `service_name="farm2home-spike"`); each `<schema>.audit_log`
stays intact but unused. The dedicated `audit` schema remains a **DEFERRED** Phase-10 decision.

---

## 9. REST / lb:// Resolution — **PASS**

`LoadBalancerClientConfig` in-process rewrite — **unchanged**. No Eureka, no
`SimpleDiscoveryClient`. **After Group 4 every `lb://` edge among the 8 embedded services is a
loopback:**

| Caller → target | Was | Now | Verified |
|---|---|---|---|
| **payment → `lb://order-service`** (`GET /orders/{id}` — validate order on payment initiation) | not embedded | EMBEDDED | ✅ `POST /payments` → loopback logged (`ReactorNetty`, 127.0.0.1, forwarded correlation-id) → order resolved → clean 409 on ₹0 wallet |
| **delivery → `lb://order-service`** (`resolveOrder` in `manualAssign`) | not embedded | EMBEDDED | ✅ `POST /delivery/assignments` → 201; loopback `GET /orders/{id}` logged |
| **delivery → `lb://payment-service`** (`PaymentServiceClient.hasPayableProgress` → `GET /payments/order/{id}/payment-exists`) | not embedded | EMBEDDED | endpoint reachable in-process (same mechanism) |
| **customer → `lb://delivery-service`** (`DeliveryRouteSelectionServiceImpl` → `GET /delivery/routes/search?active=true`) | degraded (empty) | EMBEDDED | ✅ `GET /delivery/routes/search` returns 200 in-process; the caller already tolerated failure so it just now gets real routes |
| order → customer / inventory / production | EMBEDDED (G1–3) | EMBEDDED | ✅ re-verified (§19) |

`order` still does **not** call payment or delivery via REST (only reacts to delivery events via
the stopped consumer). **No embedded service calls a NOT-embedded one** (notification / invoice /
dashboard / reports — Groups 5–7). Authorization header is forwarded on every loopback
(`RequestHeaderForwarder`, Group-2 change). No routing loop, no forged headers, no JWT bypass.

---

## 10. Payment Gateway Configuration — **PASS**

`PaymentServiceImpl` requires a `PaymentGatewayProvider`. The concrete providers are plain
classes `@Bean`-wired by the excluded `PaymentGatewayConfig`, so **NEW `PaymentGatewayAppConfig`**
replicates it exactly:

- `@EnableConfigurationProperties(RazorpayProperties.class)` — that class is in the never-scanned
  `payment.gateway.razorpay` package; its `key-secret` / `webhook-secret` fields default to `""`
  and are Actuator-sanitised.
- `payment.gateway.provider=mock` **(default via `matchIfMissing`)** → `MockPaymentGatewayProvider`
  — **no external HTTP, no credentials.** Every test and the default deployment use this.
  Test-asserted: exactly one `PaymentGatewayProvider` bean, and it is the mock.
- `payment.gateway.provider=razorpay` → only when `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET` are
  supplied via the environment (never in source), using a plain (non-`lb://`) `WebClient`.

`application.yml` carries `payment.gateway.{provider,razorpay.*,reconciliation.enabled}` as
env-var passthroughs — **no secret values**. Startup verified with no external gateway
reachable (mock provider).

---

## 11. Wallet Behavior — **PASS** (the known backlog item is already fixed in the service)

`GET /wallets/me` auto-creates a ₹0 wallet on first access. The documented backlog issue
("`debitForPayment` throws *Wallet not found* on a customer's first-ever WALLET payment") is
**no longer present** — `WalletServiceImpl.debitForPayment` (its own in-code comment documents
the fix) now does `walletRepository.findByCustomerIdForUpdate(id).orElseGet(() -> save(new
Wallet(id)))`, so a first-use WALLET payment gets a clean
`InsufficientBalanceException("Insufficient wallet balance. Available: ₹0, …")` — **verified
live** (`POST /payments` WALLET as a fresh customer → HTTP 409 with that exact message).

Classification: **pre-existing business behaviour, already corrected in the service — not an
aggregation issue, no change needed.** (The wallet row created during the failed payment is
rolled back with the transaction — no residue; test-verified.)

---

## 12. Payment Idempotency — **PASS (documented, unchanged)**

Read from code, not invented:

- **Payment creation** (`PaymentServiceImpl.initiate`) — guards against a second payment for an
  order that already has a SUCCESS/PENDING payment (`GET /payments/order/{id}/payment-exists`
  is the query delivery also uses). No client-supplied idempotency key.
- **Webhook / verify** — status transitions are guarded by the current `PaymentStatus` (only a
  PENDING payment moves), so a re-delivered webhook or a repeated `verify()` is a safe no-op.
- **Reconciliation job** — reuses `verify()`'s exact transition logic; never duplicates it.

No new idempotency mechanism was added.

---

## 13. Legacy Callback Behavior — **PASS**

`POST /api/v1/payments/callback` — `PaymentController` reads
`@Value("${farm2home.payment.legacy-callback-enabled:false}")`; when false it returns
`404 Not Found` (behaves as if the route doesn't exist). The app `application.yml` sets it
**explicitly `false`** (`${PAYMENT_LEGACY_CALLBACK_ENABLED:false}`). Verified live: FARM_MANAGER
with a valid body → **404**. Not re-enabled just because payment is now in-process. Real
outcomes still flow via `POST /{id}/verify` or the HMAC-verified `POST /webhook`.

---

## 14. Delivery Behavior — **PASS WITH CONDITIONS**

- REST endpoints (assignments manual-assign / list / status / delay, partners, routes,
  locations) — all work in-process; authorization + ownership preserved (§5–6).
- `manualAssign` → validates the order via `lb://order-service` loopback (verified).
- **Kafka-dependent, unavailable with no broker:** `OrderEventConsumer` (auto-assign a partner
  on `ORDER_CREATED`). A new order therefore stays **unassigned** until an admin runs
  `manualAssign` — which is exactly the service's designed fallback ("if no eligible partner /
  no event, the order stays unassigned for manual assignment"). `DeliveryEventProducer`
  (`DELIVERY_ASSIGNED`/status events → order-service + notification-service) also does not fire.
- **Kafka was NOT replaced with synchronous calls**; no in-process bridge built (per scope).

---

## 15. Kafka Behavior — **PASS**

- **Broker dependency: none.** Startup never touches a broker.
- **Consumers — all stopped** (test-asserted via `KafkaListenerEndpointRegistry`):
  - delivery `OrderEventConsumer` via the custom `orderKafkaListenerContainerFactory` —
    `KafkaDisabledConfig`'s BPP forces `autoStartup=false` (startup log confirms).
  - order's 2 consumers (`kafkaListenerContainerFactory`), customer's consumer
    (`customerKafkaListenerContainerFactory`) — still stopped.
- **Producers — lazy, non-fatal:** payment `paymentKafkaTemplate`, delivery
  `deliveryKafkaTemplate` — each service's own `KafkaConfig` sets `max.block.ms=3000`, each
  producer class wraps `send()` in try/catch. A publish on a mutating request fails fast and is
  logged; never rolls back the DB transaction, never blocks startup.
- **Unavailable (documented):** payment status-change events (`payment.event` listener not
  scanned); delivery auto-assignment + delivery-status events. All belong to the deferred
  in-process event bridge.

---

## 16. Async Configuration — **PASS**

`payment.config.PaymentAsyncConfig` declares `@Bean("paymentEventExecutor")` for
`PaymentEventListener`'s `@Async`. Since `payment.event` is **not scanned** (its listener only
does a dead Kafka publish, and `PaymentServiceImpl` uses `ApplicationEventPublisher` directly),
**that executor is not needed** and is not created — no app-side async config, no executor
collision. Test-asserted `paymentEventExecutor` bean is absent. `@EnableAsync` remains
platform-wide via common-core `AuditAutoConfiguration` (its `auditTaskExecutor` is the only
`@Async` executor; unique name, no collision).

---

## 17. Scheduler Behavior — **PASS**

- delivery has **no** `@Scheduled` / `@EnableScheduling` — nothing to do.
- payment `PaymentReconciliationJob` (`payment.scheduler`, now scanned) —
  `@Scheduled(fixedDelay ${…:300000})` = every 5 min; re-checks stale PENDING gateway payments
  via `PaymentService.syncStatus`. Double-guarded: `@ConditionalOnProperty(payment.gateway.
  reconciliation.enabled, matchIfMissing=true)` **and** the app-level `SchedulingConfig`
  `@EnableScheduling`. Under the mock provider every check is a **no-op** (`MockPaymentGatewayProvider`
  reports UNKNOWN). Reuses `verify()`'s logic — never duplicates a transition.
- **One `TaskScheduler`** (`spring.task.scheduling.pool.size=8`), no duplicate scheduling.
- **Tests:** `farm2home.scheduling.enabled=false` → `SchedulingConfig` conditionally absent →
  **zero `@Scheduled` tasks registered** (test-asserted) → neither the payment reconciliation
  nor order's 23:00 job can fire. **Prod keeps the scheduler ON** (Render = 1 instance, no
  ShedLock — documented, not silent).

---

## 18. Database Validation — **PASS**

8 schemas, each with its own `flyway_schema_history`:

| Schema | Migrations | Representative tables verified |
|---|---|---|
| farm / production / customer / inventory / subscription / order | 7 / 2 / 7 / 7 / 2 / 6 | (as Groups 1–3) |
| **payment** | **4** | `payments`, `wallets`, `wallet_transactions` |
| **delivery** | **8** | `delivery_assignments`, `delivery_partners`, `delivery_routes`, `delivery_locations` |

- No version collisions, no checksum errors, no cross-schema contamination (SQL-verified).
- **Safe write/read validation:**
  - payment — `POST /payments` (WALLET) drove the order-validation loopback then failed on ₹0
    balance; the auto-created wallet row **rolled back** with the transaction (no residue).
  - delivery — created a `DeliveryPartner` (FARM_MANAGER), ran `manualAssign` (creating a
    `DeliveryAssignment` via the order loopback), then **deleted both**.
- All verification test data removed; baseline restored (`payment.wallets`=1, `payment.payments`=2,
  `delivery.delivery_partners`=0, `delivery.delivery_assignments`=0, `farm.audit_log`=16,
  `order.orders`=8, `order.carts`=2). Baseline data unmodified.

---

## 19. Critical Business Flows — **PASS**

| Flow | Result |
|---|---|
| 1. order → inventory synchronous stock decrement | ✅ (Group-3 test retained; live: product order → `POST /inventory/products/{id}/decrement-stock` loopback, stock −N before response) |
| 2. order → customer → farm (2-hop nested loopback) | ✅ (Group-3 test retained; live re-verified) |
| 3. **order → payment** | order does not call payment; **payment → order** verified instead (`POST /payments` → `lb://order-service` loopback) |
| 4. **order/customer/subscription → delivery** | order/subscription don't call delivery; **customer → delivery** (`GET /delivery/routes/search`) resolves in-process; **delivery → order** verified (`manualAssign`) |
| 5. wallet first-use | ✅ auto-creates ₹0 wallet, honest `409 Insufficient wallet balance. Available: ₹0` (backlog bug already fixed in service) |
| 6. payment authorization — DELIVERY_MANAGER | ✅ **gap preserved, not widened**: `/payments/reports` DELIVERY_MANAGER → 200; `/payments/{id}` DELIVERY_MANAGER (non-owner) → **404**; SUPER_ADMIN → 200 (test-asserted) |

---

## 20. Test Results — **PASS**

`mvn -pl app verify` (app module; deps pre-installed): **BUILD SUCCESS**

```
Tests run: 32   Passed: 32   Failed: 0   Errors: 0   Skipped: 0
```

(26 after Group 3 → 32: +`paymentSchemaHasItsOwnCompleteMigrationHistory`,
+`deliverySchemaHasItsOwnCompleteMigrationHistory`, +`paymentRoleRestrictionsPreserved`,
+`deliveryRoleRestrictionsPreserved`, +`deliveryManagerPaymentDetailsGapIsPreservedNotWidened`,
+`paymentToOrderResolvesAsInProcessLoopback`; plus payment/delivery assertions folded into the
existing reachability / repository / bean / forged-header / principal-resolver tests.)

`mvn -pl app -am verify` (full reactor): **BUILD FAILURE at common-core** —
`EndToEndWorkflowIntegrationTest` → *"Could not find a valid Docker environment"*.
**Pre-existing and unrelated** — a Testcontainers test in `common-core` (not `app`);
Testcontainers cannot attach on this Windows host even though the Docker CLI works (it built +
ran the app image). Not caused by this aggregation. common-core was **not** modified. JaCoCo
gate untouched (disabled for the `app` module only).

---

## 21. Group 1 Regression — **PASS**
farm `GET /api/v1/farm` 200 · production `GET /api/v1/productions` 200 · role restrictions 403.

## 22. Group 2 Regression — **PASS**
customer list authz 401/403/200 · `/me/consents` 200 · inventory 200/403/201 ·
customer→farm `delivery-availability` loopback 200.

## 23. Group 3 Regression — **PASS**
subscription 200 + `/summary` 403/200 · order 200 + `/summary`/`/generate` 403/200 ·
order→customer→farm loopback · order→inventory synchronous decrement (stock −3). All prior
assertions retained in the 32-test suite.

---

## 24. Docker Results — **PASS**

- Docker available: **YES** (29.6.2)
- `backend/app/Dockerfile` — unchanged since Group 2.
- Image built: **190 MB content / 530 MB disk**.
- **512 MB run (`--memory=512m --memory-swap=512m`): PASS** — all 8 services 200 in-container;
  authz 401/403 correct; **OOMKilled: false**, container stayed Running, **0** OOM /
  GC-overhead markers; all 8 Flyway schemas applied; 3 Kafka listener factories stopped.

---

## 25. Memory Comparison — **PASS** (mandatory)

| | Group 3 (6 svcs) | **Group 4 (8 svcs)** | Δ |
|---|---|---|---|
| Image content size | 190 MB | 190 MB | — |
| Startup (`Started Application in`) | 20.84 s | **21.50 s** | +0.7 s |
| Ready (wall clock incl. container start) | ~23.8 s | **~24.2 s** | +0.4 s |
| Steady memory | ~348 MiB / 512 (68 %) | **~364 MiB / 512 (71 %)** | **+16 MiB** |
| Peak under light load (45 mixed reqs) | ~362 MiB / 512 (71 %) | **~376 MiB / 512 (73 %)** | **+14 MiB** |
| OOMKilled | false | **false** | — |

**+16 MiB steady / +14 MiB peak** for payment + delivery. ~136 MiB headroom at peak. The
architecture stays viable under 512 MB for 8 services. **This is not a Render-readiness claim.**

---

## 26. New Discoveries

1. **Wallet first-use bug is already fixed** in `WalletServiceImpl.debitForPayment` (auto-create
   + honest ₹0 error). The backlog item can be closed. **PASS.**
2. **`payment.event` / `PaymentAsyncConfig` are dead weight in the aggregate** — the only
   `@Async("paymentEventExecutor")` consumer is a Kafka publisher; skipping the package removes
   the executor question entirely. **PASS.**
3. **`PaymentGatewayConfig`'s `mock` default (`matchIfMissing=true`)** means the aggregate never
   needs external payment credentials. **PASS.**
4. **`payment`/`delivery` schema names are ordinary words** (unlike `order`) — no quoting needed.
   **PASS.**
5. **`kafkaObjectMapper` hijack (Group-3 issue) does not recur** — payment/delivery `KafkaConfig`
   declare no `ObjectMapper` bean; the app `@Primary ObjectMapper` still governs MVC JSON.
   **PASS.**
6. `GET /wallets/me` and `GET /order/cart` are get-or-create — a synthetic test JWT leaves one
   row each; the test suite / verification cleans them. Not a defect. **PASS (documented).**

---

## 27. Blockers — **none**

No FAIL items. No concrete blocker to Group 5.

---

## 28. Known Limitations — **DEFERRED**

- Kafka event flows for payment/delivery (and all prior groups) are dormant — no broker.
  delivery auto-partner-assignment specifically means new orders stay unassigned until manual
  assignment. → in-process event bridge, a later phase.
- 3-hop nested in-process loopbacks (e.g. delivery → order, payment → order, order → customer →
  farm) consume one servlet thread per hop; fine at normal load, a single-JVM tradeoff.
- Aggregate audit `service_name` is uniform ("farm2home-spike"). → Phase-10 `audit` schema.
- Business scheduler OFF in tests (property) — prod ON, 1 instance, no ShedLock.
- Render memory fit is verified only for the current 8-service footprint (~364 MiB steady).

---

## 29. Exact Files Changed

**New (this group), under `backend/app/`:**
- `src/main/java/com/farm2home/app/config/PaymentGatewayAppConfig.java`
- `src/main/resources/db/migration/payment/V1–V4` (4 files, verbatim copies)
- `src/main/resources/db/migration/delivery/V1–V8` (8 files, verbatim copies)

**Modified (this group):**
- `backend/app/pom.xml` — +2 `<classifier>lib</classifier>` deps
- `backend/app/src/main/java/com/farm2home/app/Application.java` — +11 scan packages, Group-4 doc
- `backend/app/src/main/java/com/farm2home/app/config/SpikeJpaConfig.java` — +2 repo/entity packages
- `backend/app/src/main/java/com/farm2home/app/config/SpikeFlywayConfig.java` — +`paymentFlyway`, +`deliveryFlyway`
- `backend/app/src/main/java/com/farm2home/app/security/SpikeSecurityConfig.java` — +`POST /api/v1/payments/webhook` permitAll
- `backend/app/src/main/resources/application.yml` — `payment.gateway.*`, `farm2home.payment.legacy-callback-enabled`
- `backend/app/src/test/java/com/farm2home/app/SpikeSingleJvmTests.java` — 26 → 32 tests

**No change** to any service module, any migration SQL, any `common-*` (the Group-2
`RequestHeaderForwarder` change stands — **Group 4 made zero shared-code changes**),
`backend/pom.xml`, or any Dockerfile other than the app's own (unchanged since Group 2).

---

## 30. Architecture Impact

| | |
|---|---|
| Group 1 / 2 / 3 / 4 | **PASS** |
| Full aggregation (13 services) | **NOT DONE** — 8/13 embedded |
| Render | **NOT READY** (no deploy; 512 MB verified for the current 8-service footprint at ~364 MiB steady / ~376 MiB peak) |

One `SecurityFilterChain`, one `EntityManagerFactory`, one `DispatcherServlet`, one
`TaskScheduler`, one Hikari pool, **8** Flyway instances, one `@Async` executor, one
`PaymentGatewayProvider` (mock), no Eureka / Config Server / Kafka broker / second datasource —
all held. Every inter-service `lb://` edge among the 8 is now an in-process loopback.

---

## 31. Recommendation for Group 5

**Stop and review before implementing Group 5** (notification-service + invoice-service).

Group 5 characteristics to weigh: notification-service is **consumer-only** (its entire job is
Kafka; with no broker it will start but process nothing — its REST endpoints
`/notifications/me`, `/logs` still work); it also has SMS/email providers (common-core
`SmsAutoConfiguration` / `PushAutoConfiguration` default to logging providers — safe). invoice-
service calls `lb://order-service` + `lb://customer-service` + `lb://payment-service` (all now
embedded) and generates PDFs (common-export). Neither has a schema-name reserved-word issue.
Do not begin until this Group 4 change is reviewed.

---

*Working tree left uncommitted on `spike/render-single-jvm`. Full P1-1 diff: `backend/pom.xml`
(Phase 2), `backend/app/` (Groups 1–4),
`common-observability/ObservabilityAutoConfiguration.java` (spike),
`common-web/RequestHeaderForwarder.java` (Group 2). The ` M` on
`common-web/.../RequestHeaderForwarderTest.java` predates this work.*
