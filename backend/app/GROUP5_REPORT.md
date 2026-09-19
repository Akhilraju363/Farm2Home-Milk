# Farm2Home Group 5 Aggregation Report

Branch: `spike/render-single-jvm` · 2026-09-07 · **Nothing committed or pushed.**
Scope: notification-service + invoice-service only. Group 6 (dashboard, reports) and Group 7
(auth) not touched. Phase-10 audit schema not touched. Kafka not replaced.

Every item is classified **PASS / PASS WITH CONDITIONS / FAIL / DEFERRED**.

---

## 1. Status — **PASS**

`backend/app` now embeds **10 business services** — farm, production, customer, inventory,
subscription, order, payment, delivery, **notification, invoice** — in one servlet JVM.
**38/38** aggregation tests green (`mvn -pl app verify` → BUILD SUCCESS). Verified live: jar and
a 512 MB Docker container. Groups 1–4 regression intact.

invoice-service's cross-service composition (`invoice → order`, `invoice → payment`,
`invoice → customer`) resolves as **real in-process loopback HTTP calls** authenticated by a
per-process proof-of-origin secret — verified in the container from the request logs (127.0.0.1,
`ReactorNetty`, `x-internal-auth` present). PDF generation produces a valid non-empty
`%PDF-1.6 … %%EOF` document in memory. notification-service's REST surface works; its
Kafka consumer is not registered (package not scanned) so nothing is left running.

---

## 2. Services Added — **PASS**

- **notification-service** — notification log query/summary/mark-read REST
  (`/api/v1/notifications/**`), SMS + push + email provider abstraction, MapStruct mapper, one
  `CustomerServiceClient` (`lb://customer-service`), **Kafka consumer-only**
  (`NotificationEventConsumer` — the entire event-driven path).
- **invoice-service** — invoice generate/list/get/pdf REST (`/api/v1/invoices/**`), composes
  each invoice from order + payment + customer data via three `lb://` clients, renders a PDF
  with Apache PDFBox 3.0.3, no Kafka, no scheduled jobs.

---

## 3. Packaging — **PASS**

Established thin-`-lib` pattern. Both added to `app/pom.xml` as `<classifier>lib</classifier>`.

| | notification-service | invoice-service |
|---|---|---|
| main `*.jar` still executable (`Start-Class`) | ✅ | ✅ |
| thin `*-lib.jar` produced | ✅ | ✅ |
| `-lib.jar` contains `application*.yml` / `db/migration/**` / logback / bootstrap | **0** | **0** |

`app/target/app.jar` — executable, bundles all 10 service `-lib.jars`, exactly one
`application.yml`. Standalone service packaging + every per-service Dockerfile untouched.
(invoice-service still has no Dockerfile of its own — pre-existing, noted in the CI/CD memo, not
in scope here.)

---

## 4. Component Scanning — **PASS**

`Application.java` — same `FullyQualifiedAnnotationBeanNameGenerator`, extended:

| Service | Scanned |
|---|---|
| notification | `.controller` `.service` `.mapper` `.client` |
| invoice | `.controller` `.service` `.client` (no `.mapper` package exists) |

**Never scanned** — every `config` package, plus:

| Excluded | Why / handled by |
|---|---|
| `notification.config.SecurityConfig` / `JpaConfig` / `AuditorAwareImpl` / `WebClientConfig` / `GatewayHeaderAuthFilter` / `UserPrincipal` / `OpenApiConfig` | app `Spike*` configs (one of each) + `ServiceUserPrincipalArgumentResolver` |
| `invoice.config.*` (same set) | same |
| **`notification.config.EmailConfig`** | **NEW app `NotificationEmailAppConfig`** (§9) — replicated verbatim; `NotificationServiceImpl` needs `EmailService` |
| **`com.farm2home.notification.kafka`** (whole package) | **NOT scanned.** Its `KafkaConfig` declares a `@Bean` method literally named `kafkaListenerContainerFactory` — identical to order-service's already-scanned one. The FQN generator renames scanned **classes**, not `@Bean` **methods**, so the two would collide (`BeanDefinitionOverrideException`). notification is **consumer-only**: with no broker the consumer + its factory are inert, and `NotificationServiceImpl` (REST) injects nothing from `notification.kafka`. Leaving the package unscanned is the clean resolution. Test-asserted: `NotificationEventConsumer` bean is absent. |
| `invoice` — no Kafka, no scheduler, no event package | nothing to exclude beyond `config` |

`SmsService` / `PushService` come from common-core `SmsAutoConfiguration` /
`PushAutoConfiguration` (active since Group 1) → `LoggingSmsProvider` / `LoggingPushProvider` by
default. No new auto-configuration was enabled.

---

## 5. Security — **PASS**

**SecurityFilterChain count: 1** (test-asserted). One `@EnableWebSecurity` / `@EnableMethodSecurity`.

**No new public endpoints.** Both services' own `SecurityConfig` permit only
`PUBLIC_ENDPOINTS_BASE` (health / api-docs / swagger), already covered.

### Authorization matrix — notification (verbatim from `NotificationLogController`)

| Endpoint | Rule |
|---|---|
| `GET /api/v1/notifications/logs` (requires `?recipientId=`), `GET /logs/{id}` | `hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)` |
| `GET /api/v1/notifications/summary` | `hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| `GET /api/v1/notifications/me`, `PATCH /{id}/read`, `PATCH /read-all` | authenticated + `@AuthenticationPrincipal` (`recipientId` = caller's own userId, never client-supplied) |

### Authorization matrix — invoice (verbatim from `InvoiceController`)

| Endpoint | Rule |
|---|---|
| `POST /api/v1/invoices/generate/{orderId}`, `GET /api/v1/invoices` (list) | `hasAnyRole(FARM_MANAGER, SUPER_ADMIN)` → `ROLE_FARM_MANAGER` / `ROLE_SUPER_ADMIN` |
| `GET /api/v1/invoices/me` | authenticated, own-scoped by stored `customerId` |
| `GET /api/v1/invoices/{id}`, `/order/{orderId}`, `GET /{id}/pdf` | authenticated + `@AuthenticationPrincipal`; service-layer ownership (`InvoiceServiceImpl.findById` — admin sees any, else `customerId` must match) |

Note `hasAnyRole(...)` (invoice, farm) vs `hasAnyAuthority(...)` (everyone else): the JWT filter
grants **both** the bare (`FARM_MANAGER`) and `ROLE_`-prefixed (`ROLE_FARM_MANAGER`) authority
per role, so both styles work under one chain — no rule rewritten.

### Loopback trust — **hardened this group** (see §9)

The pre-Group-5 filter trusted any `X-User-*` from a loopback TCP peer. That is unsafe on this
host: **every local `curl` and every `TestRestTemplate` call also originates from 127.0.0.1**, so
a forged `X-User-*` would have authenticated. Fixed: the `X-User-*` system-identity branch now
requires the request to also present the per-process **`X-Internal-Auth`** secret
(`InternalCallToken`, constant-time compared) **and** a loopback peer. This is the single-JVM
analogue of the api-gateway ↔ service `X-Internal-Auth` trust (`common-web` `GatewayTrust`).

### Verified (live in container + tests)

no JWT → **401** · bad JWT → **401** · refresh-token-as-access → **401** ·
forged `X-User-*` no JWT → **401** (invoice, customers, orders) ·
forged `X-Internal-Auth` guess + `X-User-*` → **401** ·
`invoices` list CUSTOMER **403** / FARM_MANAGER **200** ·
`notifications/summary` CUSTOMER **403** / DELIVERY_MANAGER **200** ·
`notifications/logs?recipientId=` CUSTOMER **403** / FARM_MANAGER **200** ·
`invoices/generate/{orderId}` CUSTOMER **403** (method security blocks before any loopback) ·
`notifications/me` + `invoices/me` any authenticated caller **200**.

**No authorization weakened; no method-level rule replaced by an endpoint permit.**

---

## 6. @AuthenticationPrincipal — **PASS**

`ServiceUserPrincipalArgumentResolver` — **unchanged**. `NotificationLogController` (3 methods)
and `InvoiceController` (4 methods) inject `@AuthenticationPrincipal <svc>.config.UserPrincipal`,
both records with the identical `(UUID userId, String mobile, Set<String> roles)` canonical
constructor already handled by the resolver. Verified live: `GET /notifications/me` (reads
`principal.userId()` → 200), `GET /invoices/me` (own-scoped list → 200) — the resolver produced a
usable principal, not a 500. No controller/service modified.

---

## 7. JPA — **PASS**

- **EntityManagerFactory count: 1**. No second datasource. Same `spike-hikari` pool (max 10).
- One `@EnableJpaRepositories` extended with `notification.domain.repository`,
  `invoice.domain.repository`.
- Repository beans discovered once (test-asserted): `notificationLogRepository`,
  `notificationTemplateRepository`, `invoiceRepository` (+ the prior 21 + one `AuditLogRepository`).
- No duplicate repositories, no overriding (`allow-bean-definition-overriding` **not** set).
- Entities: notification `@Table(schema="notification")`, invoice `@Table(schema="invoice")` —
  all qualified; `hibernate.default_schema=farm` only affects the shared unqualified `AuditLog`.

---

## 8. Flyway — **PASS**

`spring.flyway.enabled=false`; two new beans:

| Bean | Schema | Location | Migrations applied |
|---|---|---|---|
| `notificationFlyway` | `notification` | `classpath:db/migration/notification` | **10** (V1–V10) |
| `invoiceFlyway` | `invoice` | `classpath:db/migration/invoice` | **2** (V1–V2) |

12 SQL files copied **byte-for-byte** (checksums content-only → validate cleanly against the
existing `<schema>.flyway_schema_history`). Version numbers overlap numerically with other
services — the per-schema locations isolate them; that is the whole mechanism. SQL-verified:
`notification.flyway_schema_history` holds only its 10 descriptions (`init notification schema` …
`standardize email template layout`), `invoice` only its 2 (`init invoice schema`,
`create audit log`) — **no cross-schema contamination, no collisions, no checksum errors**.

All 10 histories (versioned rows): farm 7 · production 2 · customer 7 · inventory 7 ·
subscription 2 · order 6 · payment 4 · delivery 8 · **notification 10** · **invoice 2**.

`EntityManagerFactoryDependsOnPostProcessor("spikeFlywayMigrations")` still orders all 10
Flyway `migrate()` calls before Hibernate `validate`.

**Audit-log decision UNCHANGED** — every service's audit rows land in `farm.audit_log`
(`service_name="farm2home-spike"`); each `<schema>.audit_log` (incl. the new
`notification.audit_log`, `invoice.audit_log`) stays intact but unused. Dedicated `audit`
schema remains a **DEFERRED** Phase-10 decision.

---

## 9. REST / lb:// Resolution — **PASS**  *(STEP 11 — the most important item)*

`LoadBalancerClientConfig` in-process rewrite (`lb://<svc>/path` → `http://localhost:<port>/path`).
No Eureka, no `SimpleDiscoveryClient`, `spring.cloud.{discovery,loadbalancer}.enabled=false`.

**Change this group:** the shared `loadBalancedWebClientBuilder`'s loopback filter now stamps a
per-process **`X-Internal-Auth`** secret on every rewritten `lb://` request. Reason: invoice and
notification clients forward a **fixed system identity** (`X-User-Id=0000…`,
`X-User-Roles=SUPER_ADMIN` via `SystemIdentityHeaders` / `addSystemIdentity`) rather than a
bearer token, because the original caller's role (e.g. `FARM_MANAGER` generating an invoice) is
too narrow for customer-service's `GET /{id}` (SUPER_ADMIN/DELIVERY_MANAGER only). The filter
trusts that identity only with the secret **+** a loopback peer.

| Caller → target | Was | Now | Verified (container) |
|---|---|---|---|
| **invoice → `lb://order-service`** `GET /api/v1/orders/{id}` | not embedded | EMBEDDED loopback | ✅ log: `GET /api/v1/orders/e3bb90cf-… clientIp=127.0.0.1 user-agent=ReactorNetty x-internal-auth=<secret> x-user-roles=SUPER_ADMIN` → order resolved (orderNumber, items, totalAmount in the invoice) |
| **invoice → `lb://payment-service`** `GET /api/v1/payments/order/{id}` | not embedded | EMBEDDED loopback | ✅ log: `GET /api/v1/payments/order/e3bb90cf-…` same headers → returned no payment for that order (`payment:null`) — a **real** empty result, auth succeeded |
| **invoice → `lb://customer-service`** `GET /api/v1/customers/{id}` + `GET /api/v1/customers/{id}/addresses` | not embedded | EMBEDDED loopback | ✅ log: both calls, same headers → `customerName`, `customerMobile`, `customerEmail` populated on the invoice from the real customer row |
| **notification → `lb://customer-service`** (`CustomerServiceClient`) | not embedded | EMBEDDED loopback | same mechanism; only exercised from the (stopped) Kafka consumer path, so not hit at runtime with no broker |

**Nothing left the container.** The only external connection is Postgres
(`host.docker.internal:5432`); every `lb://` hop terminated on `localhost:8080` inside the
container. No Eureka lookups, no outbound HTTP to any service host.

**Not mocked.** A non-existent order id returns a genuine downstream 404 (invoice-service's
`OrderServiceClient` has no `.onErrorResume(NotFound)`, so it surfaces as 500 — see §26.2, a
pre-existing service bug, identical standalone). A real order id returns a real composed invoice.

Authorization header is still forwarded on bearer-carrying loopbacks (order/customer/payment/
delivery clients via `RequestHeaderForwarder`, Group-2 change) — the filter checks the bearer
first, so the added `X-Internal-Auth` on those requests is inert.

**app-side config replacement — `NotificationEmailAppConfig`** (replicates the excluded
`notification.config.EmailConfig` exactly): `email.enabled=false` **(default)** → always
`LoggingEmailProvider`, regardless of `email.provider` — **no SMTP, no `JavaMailSender`, no
credentials** (test-asserted: `emailProvider` is `LoggingEmailProvider`, zero `JavaMailSender`
beans). `spring.mail.host` is deliberately unset so Boot never auto-configures a mail sender.
`app.mail.from` added to `application.yml` as the "From" label only.

---

## 10. Notification Provider Configuration — **PASS**

| Provider | Bean source | Default in aggregate | External I/O / credentials |
|---|---|---|---|
| SMS | common-core `SmsAutoConfiguration` | `LoggingSmsProvider` | none |
| Push | common-core `PushAutoConfiguration` | `LoggingPushProvider` | none |
| Email | app `NotificationEmailAppConfig` | `LoggingEmailProvider` | none |

All three log the message and return success. No Twilio / FCM / SMTP client is constructed. No
`SMS_*` / `FCM_*` / `MAIL_*` credential is read (the yml keys are env passthroughs defaulting to
empty / disabled). A real send path would require explicitly setting `email.enabled=true` +
`spring.mail.host` (or the SMS/push equivalents) in the environment — never in source.

**Unavailable (documented):** the event-driven notification pipeline
(`NotificationEventConsumer` → `NotificationServiceImpl.process` → render template → send via
provider → write `notification_logs`) does not run — its `@KafkaListener` package is not scanned
and there is no broker. So order/delivery/payment/subscription/customer/OTP events do **not**
produce notification rows in the aggregate. The **REST** surface
(`/notifications/me|logs|summary`, `PATCH …/read`) is fully functional against existing rows.
This is the same "Kafka dormant, REST live" position as every prior group — **Kafka was NOT
replaced with an in-process bridge** (out of scope).

---

## 11. Invoice → Order / Payment / Customer — **PASS**

Covered in §9. Summary of the live container run (`POST /api/v1/invoices/generate/{orderId}` as
FARM_MANAGER, real order `e3bb90cf-…`):

- **HTTP 201 CREATED**, `INV-2026-000010`, with `orderNumber`, `orderDate`, `orderStatus`,
  line items, `customerName="Akhil Dalalii"`, `customerMobile`, `customerEmail`, `subtotal`,
  `totalAmount` — every field sourced from a different downstream service.
- Request log shows the **4 loopback calls** (`GET /orders/{id}`, `GET /payments/order/{id}`,
  `GET /customers/{id}`, `GET /customers/{id}/addresses`), all `clientIp=127.0.0.1`,
  `user-agent=ReactorNetty/1.1.13`, `x-internal-auth=<per-process secret>`,
  `x-user-roles=SUPER_ADMIN`.
- No Eureka, no external host contacted. Invoice row cleaned up afterwards.

---

## 12. Invoice PDF Generation — **PASS**

- **Library:** Apache PDFBox `3.0.3` (`org.apache.pdfbox:pdfbox`, from invoice-service's own
  pom; bundled in `invoice-service-lib.jar`). **Not** common-export, not an external service.
- **Rendering:** `InvoicePdfRenderer.render(InvoiceResponse) → byte[]` — `try (PDDocument doc =
  new PDDocument())` … `doc.save(new ByteArrayOutputStream())`. **Fully in memory.**
- **Temp files:** none. **S3 / disk:** none. No `File.createTempFile`, no filesystem write.
- **Verified (container):** `GET /api/v1/invoices/{id}/pdf` as FARM_MANAGER →
  `HTTP 200`, `Content-Type: application/pdf`, `size=1218` bytes, first bytes `%PDF-1.6`, last
  bytes `…%%EOF\n`. Test-asserted (`invoiceGenerationDrivesInProcessLoopback…AndRendersPdf`):
  status 200, `application/pdf`, `%PDF-` header, length > 500.
- **Cleanup:** the generated invoice row is deleted after each check; no temp artifacts to clean
  (there are none).
- **Memory:** a single ~1.2 KB document per request; PDBox `PDDocument` is closed by
  try-with-resources. No measurable heap impact under the load burst (§25).

---

## 13. ObjectMapper — **PASS**

MVC JSON still uses the app's `@Primary ObjectMapper` (`AppJacksonConfig`, via
`Jackson2ObjectMapperBuilder`). Neither notification nor invoice declares an `ObjectMapper`
`@Bean` (notification's `kafka.KafkaConfig` is unscanned anyway; invoice has no Kafka). The
Group-3 `kafkaObjectMapper` hijack does not recur. Test-asserted: the primary `ObjectMapper` is
the `objectMapper` bean and is **not** `kafkaObjectMapper`.

---

## 14. Async / Scheduling — **PASS**

- **notification** — no `@EnableScheduling`, no `@Scheduled`, no `@EnableAsync`, no `@Async`
  anywhere in its source (grep-verified). Nothing to wire.
- **invoice** — same: none.
- The app `SchedulingConfig` (`@ConditionalOnProperty farm2home.scheduling.enabled`,
  `matchIfMissing=true`) + one shared `TaskScheduler` (`pool.size=8`) is unchanged. Tests set
  `farm2home.scheduling.enabled=false` → `SchedulingConfig` absent → **0** `@Scheduled` tasks
  registered (test-asserted). Prod keeps it ON (Render = 1 instance, no ShedLock — documented).
- `@EnableAsync` remains platform-wide via common-core `AuditAutoConfiguration`
  (`auditTaskExecutor`, unique name, no collision). Group 5 added no executor.

---

## 15. Database Validation — **PASS**

10 schemas, each with its own isolated `flyway_schema_history` (§8). Representative tables
verified present:

| Schema | Migrations | Tables verified |
|---|---|---|
| farm / production / customer / inventory / subscription / order / payment / delivery | 7/2/7/7/2/6/4/8 | (as Groups 1–4) |
| **notification** | **10** | `notification_logs`, `notification_templates` (+ `audit_log`) |
| **invoice** | **2** | `invoices` (+ `audit_log`) |

- No version collisions, no checksum errors, no cross-schema contamination (SQL-verified).
- **Safe write/read validation:**
  - invoice — generated a real invoice (drove all 3 downstream loopbacks), read it back, fetched
    its PDF, then **deleted** the `invoice.invoices` row.
  - notification — exercised `GET /logs`, `/summary`, `/me` (reads only; no rows created).
- **Baseline restored** (SQL-verified after cleanup): `invoice.invoices`=0,
  `notification.notification_logs`=0, `farm.audit_log`=16 (0 new spike rows),
  `order.carts`=2 (one synthetic-principal cart from the `GET /cart` test was removed),
  `payment.wallets` no zero-balance residue, no `2099-12-31` test orders. Baseline data
  unmodified.

---

## 16. Critical Business Flows — **PASS**

| Flow | Result |
|---|---|
| 1. order → inventory synchronous stock decrement | ✅ Group-3 test retained |
| 2. order → customer → farm (2-hop nested loopback) | ✅ retained + live re-verified (`customers/{id}/delivery-availability` → 200) |
| 3. payment → order loopback | ✅ Group-4 test retained |
| 4. customer → farm / delivery loopback | ✅ retained |
| 5. **invoice → order + payment + customer** (3 downstream services, one request) | ✅ **new** — 201, composed invoice, 4 loopback calls logged from 127.0.0.1 |
| 6. **invoice PDF** | ✅ **new** — valid `%PDF-1.6 … %%EOF`, in-memory, `application/pdf`, 1218 B |
| 7. **notification REST (no Kafka)** | ✅ **new** — `/me` 200, `/summary` 403/200, `/logs` 403/200 |
| 8. **Kafka listeners stopped** | ✅ 3 factories forced `autoStartup=false`; notification's consumer never registered (test-asserted absent) |

---

## 17. Kafka Behavior — **PASS**

- **Broker dependency: none.** Startup never touches a broker.
- **Consumers — all stopped:** `KafkaDisabledConfig`'s BPP forces `autoStartup=false` on the 3
  registered custom factories — `customerKafkaListenerContainerFactory`,
  `orderKafkaListenerContainerFactory` (delivery's `OrderEventConsumer`),
  `kafkaListenerContainerFactory` (order's 2 consumers). Startup log confirms all 3.
  Test-asserted via `KafkaListenerEndpointRegistry` — every container `isRunning() == false`.
- **notification's `NotificationEventConsumer` — never registered.** Its package is not scanned
  (§4). Test-asserted: no `NotificationEventConsumer` bean, no extra listener container.
- **Producers:** notification has none (consumer-only); invoice has none. No new Kafka template.
- **Not replaced.** No in-process event bridge built. Documented unavailable functionality:
  the entire event-driven notification pipeline (see §10).

---

## 18. Scheduler Behavior — **PASS**

Neither new service has any scheduled work (§14). App `SchedulingConfig` unchanged: one
`TaskScheduler`, conditional on `farm2home.scheduling.enabled` (`matchIfMissing=true`), OFF in
tests (0 `@Scheduled` registered, test-asserted), ON in prod.

---

## 19. Test Results — **PASS**

`mvn -pl app verify` (app module; deps pre-installed): **BUILD SUCCESS**

```
Tests run: 38   Passed: 38   Failed: 0   Errors: 0   Skipped: 0
```

32 → 38 (+6):
`notificationSchemaHasItsOwnCompleteMigrationHistory` (10),
`invoiceSchemaHasItsOwnCompleteMigrationHistory` (2),
`notificationRoleRestrictionsPreserved`,
`invoiceRoleRestrictionsPreserved`,
`invoiceGenerationDrivesInProcessLoopbackToOrderPaymentCustomerAndRendersPdf`,
`forgedInternalAuthSecretDoesNotUnlockSystemIdentity`.
Plus assertions folded into existing tests (`oneFlywayInstancePerSchema` 8→10,
`allRepositoriesDiscoveredExactlyOnce` +3, `noDuplicateInfrastructureBeans`
+`InternalCallToken`/`emailProvider`/`JavaMailSender`, `forgedGatewayIdentityHeadersAreIgnored`
+invoice/+notification, `validAccessTokenReachesAllEightServices…` → `…AllTenServices…` +3,
`kafkaListenerContainersDoNotAutoStart` +`NotificationEventConsumer` absent).

`mvn -pl app -am verify` (full reactor): **BUILD FAILURE at `common-core`** —
`EndToEndWorkflowIntegrationTest` → *"Could not find a valid Docker environment"*.
**Pre-existing and unrelated** — a Testcontainers test in `common-core` (not `app`);
Testcontainers cannot attach on this Windows host even though the Docker CLI works (it built and
ran the app image). Not caused by this aggregation. **`common-core` was NOT modified. JaCoCo
gate untouched** (disabled for the `app` module only; the invoice-service coverage gate seen
during a `-DskipTests` install is expected and irrelevant — full `verify` runs its tests).

---

## 20. Group 1 Regression — **PASS**
farm `GET /api/v1/farm` 200 · production `GET /api/v1/productions` 200 · `productions/summary/today`
CUSTOMER 403 / DELIVERY_MANAGER 200 · farm `POST` CUSTOMER 403.

## 21. Group 2 Regression — **PASS**
customer list 401/403(CUSTOMER,FARM_MANAGER)/200(SUPER_ADMIN,DELIVERY_MANAGER) · `/me/consents` 200 ·
inventory `/summary` 403/200 · **customer→farm `delivery-availability` loopback 200** (Authorization
forwarding still works alongside the new `X-Internal-Auth` stamp).

## 22. Group 3 Regression — **PASS**
subscription 200 + `/summary` 403/200 · order 200 + `/summary` 403/200 + `/generate` 403/not-403 ·
`reviews/my` CUSTOMER 200 / FARM_MANAGER 403 · cart 200 · order→customer→farm + order→inventory
loopbacks (retained in-suite).

## 23. Group 4 Regression — **PASS**
payment `/summary` 403/200 · `/callback` 403 then 404 (legacy off) · `/webhook` reaches handler ·
`/refund` 403 · delivery `/assignments/summary` 403/200 · `POST /delivery/partners` CUSTOMER +
DELIVERY_MANAGER 403 · `deliveryManagerPaymentDetailsGapIsPreservedNotWidened` green ·
payment→order loopback green.

---

## 24. Docker Results — **PASS**

- Docker available: **YES** (29.6.2, 8 GiB).
- `backend/app/Dockerfile` — **unchanged** since Group 2 (`eclipse-temurin:21-jre-alpine`,
  `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC`).
- Image `farm2home-app:g5` built: **532 MB** (`docker images` disk size; ~same as Group 4's
  530 MB — content unchanged, +2 thin lib jars are a few MB).
- **512 MB run (`--memory=512m --memory-swap=512m -p 8100:8080`, DB via
  `host.docker.internal`): PASS**
  - All **10** services → 200 in-container for their basic authenticated GET.
  - Security matrix in-container: no/bad/refresh JWT → 401; forged `X-User-*` → 401; forged
    `X-Internal-Auth` → 401; invoice list 403/200; notification summary + logs 403/200.
  - **Invoice generation in-container:** 201, `INV-2026-000010`, 4 loopback calls logged from
    127.0.0.1 with `x-internal-auth`.
  - **Invoice PDF in-container:** 200, `application/pdf`, `%PDF-1.6 … %%EOF`, 1218 B.
  - **Notification REST in-container:** `/me` 200, `/summary` DM 200 / CUSTOMER 403.
  - 10 Flyway schemas applied; **3** Kafka listener factories stopped; notification consumer
    absent.
  - **OOMKilled: false**, `Running=true`, `ExitCode=0`, `RestartCount=0`, **0** OOM /
    GC-overhead / FATAL markers in the log, health `UP` throughout.
- Container + image removed after the run.

---

## 25. Memory Comparison — **PASS WITH CONDITIONS** (mandatory)

| | Group 4 (8 svcs) | **Group 5 (10 svcs)** | Δ |
|---|---|---|---|
| Image size (`docker images`, disk) | ~530 MB | **~532 MB** | +2 MB |
| Startup (`Started Application in`) | 21.5 s¹ | **11.1 s**¹ | — (machine-load dependent, not comparable this run) |
| Ready (wall clock incl. container start) | ~24 s | **~15 s**¹ | — (¹) |
| **Steady memory** | ~364 MiB / 512 (71 %) | **~425 MiB / 512 (83 %)** | **+61 MiB** |
| **Peak memory** (300 mixed reqs) | ~376 MiB / 512 (73 %) | **~443 MiB / 512 (86 %)** | **+67 MiB** |
| OOMKilled | false | **false** | — |
| Headroom at peak | ~136 MiB | **~69 MiB** | **−67 MiB** |

¹ Startup/ready times this run were taken with the machine otherwise idle; Group 4's were under
load. Startup is **not** meaningfully comparable across runs on this host — memory is.

**Condition:** notification + invoice added **+61 MiB steady / +67 MiB peak** — noticeably more
than payment+delivery's +16/+14. The likely contributors: notification's `spring-boot-starter-mail`
(pulls Jakarta Mail even though `LoggingEmailProvider` is used) + Apache PDFBox + notification's
10-migration schema warm-up + two more full JPA persistence units. **Peak is now 86 % of the
512 MB cap (~69 MiB headroom).** Still no OOM, container healthy — but the remaining 3 services
(Group 6 dashboard + reports, Group 7 auth) must be watched carefully; a `MaxRAMPercentage` /
GC tuning pass or trimming `spring-boot-starter-mail` may be needed before 13/13.
**This is not a Render-readiness claim.**

---

## 26. New Discoveries

1. **Loopback-only trust was insufficient on this host.** All local `curl` / `TestRestTemplate`
   traffic is 127.0.0.1, so the pre-Group-5 "trust `X-User-*` from a loopback peer" branch would
   have authenticated a forged `X-User-*`. **Fixed** with a per-process `X-Internal-Auth` secret
   (`InternalCallToken`) stamped by the loopback WebClient filter and required (constant-time
   compared) by the JWT filter — mirrors the production gateway↔service trust model. **PASS.**
2. **invoice-service `OrderServiceClient.getOrder` has no `.onErrorResume(NotFound)`** — a
   non-existent order id throws `WebClientResponseException.NotFound` instead of returning
   `null`, so `InvoiceServiceImpl.generate`'s `if (order == null) throw ResourceNotFoundException`
   is dead code and the client gets a 500 rather than the documented 404. **Pre-existing service
   bug — identical standalone** (invoice-service would get the same from order-service). Per STEP
   21, **not fixed** — documented only. **PASS (documented).**
3. **notification's `kafkaListenerContainerFactory` `@Bean`-method name collides** with
   order-service's. The FQN bean-name generator renames scanned **classes**, not `@Bean`
   **methods**. Resolved by not scanning `notification.kafka` at all (consumer-only, inert with
   no broker). **PASS.**
4. **`notification.config.EmailConfig` needed replicating** (`NotificationServiceImpl` requires
   `EmailService`). `NotificationEmailAppConfig` does so verbatim; `email.enabled=false` default
   → `LoggingEmailProvider`, and `spring.mail.host` unset → no `JavaMailSender` auto-config.
   **PASS.**
5. **`spring-boot-starter-mail` on the classpath** (from notification) — Jakarta Mail classes
   load regardless of provider choice; a contributor to the +61 MiB. Trimming it to a plain
   `jakarta.mail` optional dependency is a candidate optimisation for a later group. **Noted.**
6. **notification / invoice schema names are ordinary words** — no reserved-word quoting (unlike
   `order`). **PASS.**
7. **`GET /api/v1/notifications/logs` without `?recipientId=` → 500** (not 403/400) — the
   `@RequestParam UUID recipientId` is required and the shared `common-web`
   `GlobalExceptionHandler` maps the resulting `MissingServletRequestParameterException` to 500.
   Same pre-existing standalone behaviour seen for malformed bodies in Groups 2–4. Tests send the
   param. **PASS (documented, unchanged).**

---

## 27. Blockers — **none**

No FAIL items. No concrete blocker to Group 6. The peak-memory headroom (§25) is a **caution**,
not a blocker.

---

## 28. Known Limitations — **DEFERRED**

- Event-driven notification pipeline is dormant (no broker) — order/delivery/payment/etc. events
  produce no notification rows; REST surface works. → in-process event bridge, a later phase.
- Kafka event flows for all prior groups remain dormant (unchanged).
- invoice `OrderServiceClient` 404→500 (discovery 26.2) — left as-is per scope.
- `spring-boot-starter-mail` weight + overall +61 MiB steady — GC/deps tuning candidate before 13/13.
- Aggregate audit `service_name` uniform ("farm2home-spike"). → Phase-10 `audit` schema.
- Business scheduler OFF in tests (property) — prod ON, 1 instance, no ShedLock.
- Render memory fit verified only for the current **10-service** footprint (~425 MiB steady /
  ~443 MiB peak, 512 MB cap). **Not a Render-readiness claim.**
- `X-Internal-Auth` secret is per-process random (never leaves the container). Pinning it via
  `FARM2HOME_APP_INTERNAL_SECRET` and full gateway-replacement hardening is a later phase.

---

## 29. Exact Files Changed

**New (this group), under `backend/app/`:**
- `src/main/java/com/farm2home/app/security/InternalCallToken.java`
- `src/main/java/com/farm2home/app/config/NotificationEmailAppConfig.java`
- `src/main/resources/db/migration/notification/V1–V10` (10 files, byte-identical copies)
- `src/main/resources/db/migration/invoice/V1–V2` (2 files, byte-identical copies)

**Modified (this group):**
- `backend/app/pom.xml` — +2 `<classifier>lib</classifier>` deps (notification, invoice)
- `.../app/Application.java` — +7 scan packages (notification ×4, invoice ×3), Group-5 doc,
  `notification.kafka` exclusion comment
- `.../app/config/SpikeJpaConfig.java` — +2 entity packages, +2 repository packages
- `.../app/config/SpikeFlywayConfig.java` — +`notificationFlyway`, +`invoiceFlyway`,
  `spikeFlywayMigrations` 8→10 params
- `.../app/config/LoadBalancerClientConfig.java` — loopback filter stamps `X-Internal-Auth`
  (`InternalCallToken`)
- `.../app/security/SpikeJwtAuthenticationFilter.java` — system-identity branch now requires the
  `X-Internal-Auth` secret **+** loopback peer (was loopback peer only); constructor takes
  `InternalCallToken`; Javadoc rewritten
- `.../app/security/SpikeSecurityConfig.java` — filter bean injects `InternalCallToken`
- `.../app/resources/application.yml` — `app.mail.from`, `email.{enabled,from-name}`, header comment
- `.../app/src/test/java/com/farm2home/app/SpikeSingleJvmTests.java` — 32 → 38 tests

**No change** to any service module source, any migration SQL, any `common-*` (the Group-2
`RequestHeaderForwarder` change stands — **Group 5 made zero shared-code changes**),
`backend/pom.xml`, or any Dockerfile.

---

## 30. Architecture Impact

| | |
|---|---|
| Group 1 / 2 / 3 / 4 / 5 | **PASS** |
| Full aggregation (13 services) | **NOT DONE** — 10/13 embedded (dashboard, reports, auth remain) |
| Render | **NOT READY** (no deploy; 512 MB verified for the current 10-service footprint at ~425 MiB steady / ~443 MiB peak — 86 % of cap) |

One `SecurityFilterChain`, one `EntityManagerFactory`, one `DispatcherServlet`, one
`TaskScheduler`, one Hikari pool, **10** Flyway instances, one `@Async` executor, one
`ObjectMapper` (`@Primary`), one `PaymentGatewayProvider` (mock), one `InternalCallToken`,
logging-only SMS/push/email, no Eureka / Config Server / Kafka broker / `JavaMailSender` /
second datasource — all held. Every inter-service `lb://` edge among the 10 is an in-process
loopback; the three invoice edges are authenticated by the per-process `X-Internal-Auth` secret.

---

## 31. Recommendation for Group 6

**Stop and review before implementing Group 6** (dashboard-service + reports-service).

Group 6 characteristics to weigh: both are **BFF/aggregators** — they own no schema (or a thin
one) and call many `lb://` services via `RequestHeaderForwarder` (bearer-forwarded loopback,
already working). Main risks: (a) **memory** — peak is already 86 % of 512 MB; measure early and
consider a GC/deps pass; (b) each may ship its own `WebClientConfig` / `SecurityConfig` /
`OpenApiConfig` (same exclusion pattern); (c) reports-service uses `common-export` (Excel/CSV) —
check for temp-file usage like the invoice PDF review; (d) confirm no BFF endpoint is
accidentally made public. Do not begin until this Group 5 change is reviewed.

---

*Working tree left uncommitted on `spike/render-single-jvm`. Full P1-1 diff: `backend/pom.xml`
(Phase 2), `backend/app/` (Groups 1–5),
`common-observability/ObservabilityAutoConfiguration.java` (spike),
`common-web/RequestHeaderForwarder.java` (Group 2). The ` M` on
`common-web/.../RequestHeaderForwarderTest.java` predates this work.*
