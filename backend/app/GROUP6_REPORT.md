# Farm2Home Group 6 Aggregation Report — dashboard + reports

Branch: `spike/render-single-jvm` · 2026-09-07 · **Nothing committed or pushed.**
Scope: dashboard-service + reports-service only. Group 7 (auth), Phase-10 audit schema, Kafka
replacement — NOT touched.

Every item is classified **PASS / PASS WITH CONDITIONS / WARNING / FAIL / DEFERRED**.

---

## 1. Status — **PASS WITH CONDITIONS**

`backend/app` now embeds **12 services** in one servlet JVM: farm, production, customer,
inventory, subscription, order, payment, delivery, notification, invoice, **dashboard, reports**.
**42/42** aggregation tests green (`mvn -pl app verify` → BUILD SUCCESS). Verified live in a
512 MB Docker container: all 12 service surfaces, dashboard's 8-way parallel fan-out, and
reports-service's CSV / Excel (SXSSF) / PDF exports over a 2,508- and 6,008-row dataset.
Groups 1–5 regression intact.

**Condition:** the 12-service aggregate does **not** fit 512 MB on the default JVM flags. Under a
realistic large-export + concurrent-dashboard workload the `MaxRAMPercentage=70` config climbed
to ~510 MiB (99.8 %) and OOM-killed the diagnostic variant. It required
`-XX:MaxRAMPercentage=55 -Xss512k` (applied to the Dockerfile, measured, regression-tested) to
hold — and even then peaks at **~482–505 MiB (94–99 %)**. See §26–27.

---

## 2. Services Added — **PASS**

- **dashboard-service** — one endpoint `GET /api/v1/dashboard/summary`; a BFF that fans out to
  **8** owning services' `/summary` endpoints in parallel (`Mono.zip`), each call independently
  timeout-bounded (3 s) and degraded to a safe fallback on error. No DB, no Kafka, no schedulers.
- **reports-service** — 7 report endpoints + 7 export endpoints (`/sales`, `/customers`,
  `/subscriptions`, `/inventory`, `/production`, `/deliveries`, `/payments` and each `…/export`).
  A BFF that proxies each owning service's own `/reports` endpoint (paginated) and streams CSV /
  Excel / PDF via `common-export`. No DB, no Kafka, no schedulers.

---

## 3. Packaging — **PASS**

Established thin-`-lib` pattern. Both added to `app/pom.xml` as `<classifier>lib</classifier>`.

| | dashboard-service | reports-service |
|---|---|---|
| main `*.jar` still executable (`Start-Class`) | ✅ (66 MB) | ✅ (88 MB) |
| thin `*-lib.jar` produced | ✅ (41 KB) | ✅ (49 KB) |
| `-lib.jar` contains `application*.yml` / `logback` / `db/migration` / bootstrap | **0** | **0** |
| classes in `-lib.jar` | 27 | 26 |

`app/target/app.jar` — executable, bundles all 12 service `-lib.jars`, one `application.yml`.
**`app.jar` grew only +91 KB vs Group 5** (129,455,417 vs 129,364,335 bytes) — Group 6 adds no
new heavy dependency (see §5). Standalone service packaging + Dockerfiles untouched. (Neither
service has a Dockerfile of its own — pre-existing, unchanged.)

---

## 4. Component Scanning — **PASS**

`Application.java` — same `FullyQualifiedAnnotationBeanNameGenerator`, extended:

| Service | Scanned | Not present (nothing to exclude) |
|---|---|---|
| dashboard | `.controller` `.service` `.client` | no `.mapper`, no `.kafka` package |
| reports | `.controller` `.service` `.client` | no `.mapper`, no `.kafka` package |

**Never scanned** — both services' `.config` package: `SecurityConfig` (SecurityFilterChain),
`WebClientConfig` (`@LoadBalanced loadBalancedWebClientBuilder` — same bean name as the 11 other
excluded copies), `GatewayHeaderAuthFilter` (`OncePerRequestFilter` reading `X-User-*`),
`UserPrincipal` (record), `OpenApiConfig`. The app supplies exactly one of each.

**Bean delta: +18** (Actuator `/beans`: 771 → **789**). dashboard: `DashboardController`,
`DashboardServiceImpl`, 8 `*ServiceClient`; reports: `ReportController`, `ReportServiceImpl`,
8 `*ReportClient` / `*Client`. ~few KB each — not memory-relevant (§27).

---

## 5. Security — **PASS**

**SecurityFilterChain count: 1** (test-asserted). One `@EnableWebSecurity` / `@EnableMethodSecurity`.

**No new public endpoints.** Both services' own `SecurityConfig` permit only
`ApiConstants.PUBLIC_ENDPOINTS_BASE` (health / api-docs / swagger) — already covered.
**`anyRequest().authenticated()`** — every dashboard/reports BFF endpoint requires a valid token.

### Authorization matrix (verbatim)

| Endpoint | Rule |
|---|---|
| `GET /api/v1/dashboard/summary` | `@PreAuthorize hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |
| `GET /api/v1/reports/**` (all 14 — class-level) | `@PreAuthorize hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)` |

### Verified live (512 MB container)

| Test | dashboard | reports (`/sales`) |
|---|---|---|
| no JWT | **401** | **401** |
| CUSTOMER (unauthorized role) | **403** | **403** |
| FARM_MANAGER / DELIVERY_MANAGER (authorized) | **200** | **200** |
| refresh-token-as-access | **401** | (same chain) |
| forged `X-User-*` (no JWT) | **401** | **401** |
| forged `X-Internal-Auth` + `X-User-*` | **401** | **401** |
| valid internal loopback (bearer forwarded) | **200** (8 downstream `/summary` hops) | **200** (paged `/reports` hops) |

**Group-5 internal trust model (JWT + `X-Internal-Auth` + loopback check) is unchanged and not
weakened.** No BFF endpoint made public.

### Security fix required by Group 6 (§28.1)

reports-service's export endpoints return a `StreamingResponseBody`; Spring MVC re-dispatches
through the whole filter chain (async dispatch) once the body is written. `SpikeJwtAuthenticationFilter`
established the principal only in the `SecurityContextHolder` (stateless — no repository save),
so on the async re-dispatch `AuthorizationFilter` saw an empty context and raised
`AccessDeniedException` — **harmless post-commit** (the client already has the complete, correct
file) **but it logged an ERROR stack trace on every export**. Fixed by overriding
`shouldNotFilterAsyncDispatch()` → `false` so the (cheap) JWT filter re-runs on the async
dispatch and re-establishes the identity. After the fix: **0** `AccessDeniedException` /
"response already committed" log lines across the entire export workload. No authorization rule
changed.

---

## 6. @AuthenticationPrincipal — **PASS (N/A)**

Neither `DashboardController` nor `ReportController` injects `@AuthenticationPrincipal` — they
have no per-caller ownership logic (the downstream owning services enforce that). The
`ServiceUserPrincipalArgumentResolver` already matches any `com.farm2home.*.config.UserPrincipal`
record (its javadoc says "verified across all 12"), and both services' `UserPrincipal` records
have the identical `(UUID, String, Set<String>)` canonical constructor — so it would work if
they ever added a principal parameter, but it is never invoked for Group 6. No change.

---

## 7. JPA — **PASS**

- **EntityManagerFactory count: 1** (test-asserted). No second datasource, no second persistence
  unit, same `spike-hikari` pool.
- **`SpikeJpaConfig` NOT changed** — dashboard and reports have **no `@Entity`, no
  `JpaRepository`, no `domain` package**. Nothing to add.
- Test-asserted: zero repository beans whose name contains `dashboard` or `reports`.

---

## 8. Flyway — **PASS**

- **`SpikeFlywayConfig` NOT changed** — dashboard and reports ship **no migrations** (no
  `db/migration` directory). No `dashboardFlyway` / `reportsFlyway` invented.
- **Flyway instance count: 10** (test-asserted, unchanged): farm 7 · production 2 · customer 7 ·
  inventory 7 · subscription 2 · order 6 · payment 4 · delivery 8 · notification 10 · invoice 2.
- Container startup log confirms exactly 10 `Flyway applied` lines.

---

## 9. Dashboard downstream calls — **PASS**

`DashboardServiceImpl` — `Mono.zip` of **8 concurrent** `lb://` calls, then `.block()`.
Every target is **embedded** → every call resolves through the existing in-process loopback
rewrite (`LoadBalancerClientConfig`).

| Client → target | Endpoint | Embedded? | Verified |
|---|---|---|---|
| `CustomerServiceClient` → `lb://customer-service` | `GET /api/v1/customers/summary` | ✅ | ✅ 200, `clientIp=127.0.0.1`, `ReactorNetty`, `x-internal-auth=<secret>` |
| `SubscriptionServiceClient` → `lb://subscription-service` | `GET /api/v1/subscriptions/summary` | ✅ | ✅ |
| `OrderServiceClient` → `lb://order-service` | `GET /api/v1/orders/summary` | ✅ | ✅ |
| `DeliveryServiceClient` → `lb://delivery-service` | `GET /api/v1/delivery/assignments/summary` | ✅ | ✅ |
| `PaymentServiceClient` → `lb://payment-service` | `GET /api/v1/payments/summary` | ✅ | ✅ |
| `InventoryServiceClient` → `lb://inventory-service` | `GET /api/v1/inventory/summary` | ✅ | ✅ |
| `ProductionServiceClient` → `lb://production-service` | `GET /api/v1/productions/summary` | ✅ | ✅ |
| `NotificationServiceClient` → `lb://notification-service` | `GET /api/v1/notifications/summary` | ✅ | ✅ |

Auth: clients call `headerForwarder.forwardable()` (`common-web` `RequestHeaderForwarder`), which
forwards the caller's **bearer `Authorization`** (+ correlation id); `SpikeJwtAuthenticationFilter`
re-validates it on each loopback hop. `X-Internal-Auth` is also stamped by the loopback filter
(inert for bearer calls). **No Eureka. Nothing leaves the process** (the only external
connection is Postgres). Downstream HTTP calls were **not** converted to direct Java calls.
Live dashboard body returned real aggregated data (`totalCustomers`, `pendingOrders`,
`revenueThisMonth`, `recentNotifications`).

---

## 10. Reports downstream calls — **PASS**

Each report proxies exactly one owning service's own `/reports` endpoint (paginated). All
targets embedded.

| Client → target | Endpoint | Verified |
|---|---|---|
| `SalesReportClient` → `lb://order-service` | `GET /api/v1/orders/reports` | ✅ 200, loopback logged |
| `CustomerReportClient` → `lb://customer-service` | `GET /api/v1/customers/reports` | ✅ |
| `SubscriptionReportClient` → `lb://subscription-service` | `GET /api/v1/subscriptions/reports` | ✅ |
| `InventoryReportClient` → `lb://inventory-service` | `GET /api/v1/inventory/transactions/reports` | ✅ |
| `ProductionReportClient` → `lb://production-service` | `GET /api/v1/productions/reports` | ✅ |
| `DeliveryReportClient` → `lb://delivery-service` | `GET /api/v1/delivery/assignments/reports` | ✅ |
| `PaymentReportClient` → `lb://payment-service` | `GET /api/v1/payments/reports` | ✅ |
| `FarmCowResolverClient` → `lb://farm-service` | `GET /api/v1/farm/cows/ids` | ✅ (production-by-farm filter) |

Two reports do a 2-hop resolution first (delivery-by-customer → order-service sales report for
order ids; production-by-farm → farm-service for cow ids), bounded by `RESOLUTION_PAGE_SIZE=1000`.
Same `RequestHeaderForwarder` bearer-forwarding + loopback rewrite as dashboard. Exports page
the downstream in batches of 500 (`streamReport` / `BatchSupplier`) — the full result set is
never held as one Java collection.

---

## 11. BFF concurrency — **PASS WITH CONDITIONS**

| Aspect | Detail |
|---|---|
| Dashboard fan-out | **8** concurrent `lb://` calls per request via `Mono.zip`; `.block()` on the servlet thread. Under a 4-way parallel dashboard burst that is up to **32** concurrent in-process loopback requests → up to ~32 Tomcat exec threads spawned briefly. |
| Reports export | Sequential paging (batch 500) per export; one active downstream call at a time per export request. Concurrent *export requests* each hold their own SXSSF/PDF buffer. |
| Netty connections | One shared `reactorResourceFactory` / `ConnectionProvider`. Direct-memory arenas pinned to 2 (Group-5 fix, retained — no regression). Direct ("Other") NMT stayed ~9–12 MB. |
| Thread growth | 33 at rest → ~70–90 under the mixed parallel workload. `-Xss512k` applied to cap stack cost. |
| Response retention | Dashboard: 8 small summary DTOs zipped then GC'd. Reports non-export: one `ReportPage` (≤ default page size). Reports export: **the bounded batch (500 rows) + the format buffer** — see §15/§16. |

**Condition:** the combination of dashboard 8-way fan-out + concurrent large exports is the
memory-pressure driver (§27). Concurrency was **not reduced blindly** — the dashboard timeout +
fallback already bounds fan-out latency; the fix was JVM-level (§26).

---

## 12. Kafka behavior — **PASS**

- **Neither dashboard nor reports uses Kafka** — no `@KafkaListener`, no `KafkaTemplate`, no
  `kafka` package (grep-verified). Nothing to disable.
- Kafka listener-container factories: **still exactly 3, all `autoStartup=false`**
  (`customerKafkaListenerContainerFactory`, `orderKafkaListenerContainerFactory`,
  `kafkaListenerContainerFactory`) — container startup log confirms. Test-asserted: every
  listener container `isRunning() == false`.
- Kafka was **not** introduced or replaced.

---

## 13. Scheduler behavior — **PASS**

- **Neither service has `@Scheduled` / `@EnableScheduling`** (grep-verified). No new scheduled job.
- App `SchedulingConfig` unchanged: one shared `TaskScheduler`, `@ConditionalOnProperty
  farm2home.scheduling.enabled` (`matchIfMissing=true`), OFF in tests (0 `@Scheduled` registered,
  test-asserted), ON in prod. No legitimate business job disabled.

---

## 14. Async behavior — **PASS WITH CONDITIONS**

- **Neither service has `@Async` / `@EnableAsync`** (grep-verified). No new executor.
- reports-service **does** use Spring MVC async via `StreamingResponseBody` for every `…/export`
  endpoint. This runs on Boot's `applicationTaskExecutor` (the existing shared pooled executor) —
  **no new thread pool**. It surfaced the async-dispatch security issue fixed in §5/§28.1.
- common-core's `auditTaskExecutor` (`@Async` audit writer) remains the only `@Async` executor —
  unchanged, no collision.

---

## 15. Excel / export implementation — **PASS**

- **`common-export.ExcelTabularExporter` uses `SXSSFWorkbook(100)` — streaming**, NOT
  `XSSFWorkbook`. 100-row in-memory window, older rows flushed to **temp files**,
  `setCompressTempFiles(true)`, `workbook.dispose()` after write. Shared-string table off (inline
  strings) so no SST growth.
- **CSV** — fully streamed, `printer.flush()` after every batch. **PDF** — see §16.
- Data is **downstream-paged** (`BatchSupplier`, batch 500) — the report result set is never a
  single Java collection in reports-service.
- **Verified valid** in the 512 MB container:
  - Small (1 customer, ~4 rows): CSV 137 B, XLSX 3.5 KB, PDF 957 B.
  - Large (2,508 rows): CSV 195 KB, **XLSX 57 KB `dimension A1:E2509`, `z.testzip()==None` (valid)**, PDF 80 KB `%PDF-`.
  - Large (6,008 rows): CSV 469 KB, **XLSX 128 KB `dimension A1:E6009` valid**, PDF 186 KB valid.
  - 12× repeated large XLSX, then 20× sustained 4-way-parallel including XLSX export, then 6
    concurrent large XLSX — all HTTP 200, all valid, **no OOM**.
- **Temp files:** SXSSF spools to `java.io.tmpdir` (container writable layer = disk, **not**
  tmpfs — no RAM cost); cleaned by `workbook.dispose()`. No leaked temp files observed.
- **Memory (measured, 512 MB container, tuned flags):** SXSSF Excel export of 2,508–6,008 rows
  adds only a few MiB transient per call and returns to baseline; the memory pressure is from
  **concurrency + PDF**, not the SXSSF Excel path (§16, §27).
- Export correctness was **not weakened**.

---

## 16. PDF / export implementation — **PASS WITH CONDITIONS**

- **`common-export.PdfTabularExporter` (PDFBox 3.0.3) is NOT streaming.** Its own javadoc:
  *"a PDF's cross-reference table means the complete document object graph must be known before
  any bytes can be written"*. Rows are appended per batch to an in-memory `PDDocument`
  (`new PDDocument()` → memory-only stream cache in PDFBox 3.0), then `document.save(out)` at the
  end. `PDPageContentStream` closed per page; `PDDocument` closed via try-with-resources.
- **For a large export this is the memory hog:** 6,008 rows ≈ **300 A4-landscape pages** of
  `PDType1Font` text operators, all resident until `save()`. Measured: single large PDF export
  ~+15–25 MiB transient; 3–4 concurrent large PDF exports push the container to ~494–505 MiB.
- **No leak** — sequential large PDF exports (×15) held flat at ~485 MiB; resources are all
  try-with-resources closed; no retained buffers.
- **Not changed** — this is `common-export` shared code (invoice-service depends on PDFBox too);
  redesigning `PdfTabularExporter` to stream is out of Group 6 scope. Documented as a limitation
  (§30) with a recommendation.

---

## 17. Database validation — **PASS**

- 12 services, but only **10 own a schema** — dashboard and reports own **none** (correct; no
  schema invented).
- 10 schemas each with an isolated `flyway_schema_history` (§8) — no collisions, no
  cross-contamination (unchanged from Group 5).
- **No write path** in dashboard/reports — both are read-only proxies. Verified: exercised
  dashboard summary + all 7 report types + all 3 export formats; **no rows created anywhere**.
- **Baseline restored** after testing: `"order".orders`=8 (all 6,000 + 2,500 synthetic
  `LOADTEST-` rows deleted), `invoice.invoices`=0, `"order".carts`=2, `farm.audit_log`=16,
  `notification.notification_logs`=0. Baseline data unmodified.
- Test-data note: the synthetic `LOADTEST-` orders were first inserted with an invalid
  `order_type='MANUAL'` (no such `OrderType` enum constant → order-service report query threw
  500). **This was a test-data error, not an aggregation bug** — corrected to `ONE_TIME`; the
  report/export then worked over 6,008 rows.

---

## 18. Security validation — **PASS**

Full matrix run live in the 512 MB container for **both** dashboard and reports:
no JWT → 401 · CUSTOMER → 403 · SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER → 200 · refresh
token → 401 · forged `X-User-*` → 401 · forged `X-Internal-Auth` + `X-User-*` → 401 · valid
internal loopback → success. Test-asserted: `forgedGatewayIdentityHeadersAreIgnored` and
`forgedInternalAuthSecretDoesNotUnlockSystemIdentity` extended with the dashboard + reports
paths. **No BFF endpoint made public.**

---

## 19. Business flow validation — **PASS**

| Flow | Result |
|---|---|
| Dashboard BFF — 8-way parallel `Mono.zip` fan-out to all 8 owning services | ✅ 200, real aggregated data, all 8 loopback hops logged from 127.0.0.1 |
| Dashboard graceful degradation (one downstream slow/erroring → fallback) | ✅ by design (`withFallback`, 3 s timeout) — retained |
| Reports — proxy each owning service's `/reports` (paged) | ✅ all 7 types 200 with real data |
| Reports — 2-hop resolution (delivery-by-customer, production-by-farm) | ✅ resolves via order-service / farm-service loopback |
| Reports export — CSV (streamed) | ✅ valid, 2.5k & 6k rows |
| Reports export — Excel (SXSSF streamed, temp-file backed) | ✅ valid xlsx, `z.testzip()` clean, 2.5k & 6k rows |
| Reports export — PDF (PDFBox, non-streaming) | ✅ valid `%PDF-`, ~300 pages for 6k rows |
| Groups 1–5 regression incl. invoice→order/customer/payment loopback + PDF | ✅ invoice generate → 201 with real `customerName` |

---

## 20. Test Results — **PASS**

`mvn -pl app verify`: **BUILD SUCCESS**

```
Tests run: 42   Passed: 42   Failed: 0   Errors: 0   Skipped: 0
```

38 → 42 (+4): `dashboardRoleRestrictionsPreserved`, `reportsRoleRestrictionsPreserved`,
`dashboardSummaryFansOutToEmbeddedServicesViaInProcessLoopback`,
`reportExportStreamsValidCsvExcelAndPdfViaLoopback` (CSV/EXCEL/PDF magic-byte + size + status,
via raw `HttpURLConnection` — `TestRestTemplate`'s converter list won't read the vendor xlsx
media type as `byte[]`). Plus: `validAccessTokenReachesAllTenServices…` → `…AllTwelveServices…`
(+dashboard, +reports), `forgedGatewayIdentityHeadersAreIgnored` / `forgedInternalAuthSecretDoesNotUnlockSystemIdentity`
/ `noDuplicateInfrastructureBeans` extended for Group 6.

`mvn -pl app -am verify`: **BUILD FAILURE at `common-core`** —
`EndToEndWorkflowIntegrationTest` → *"Can't get Docker image: testcontainers/ryuk:0.8.1"* /
*"Could not find a valid Docker environment"*. **Pre-existing and unrelated** — a Testcontainers
test in `common-core` (not `app`); Testcontainers cannot attach on this Windows host even though
the Docker CLI works (it built + ran the app image). **`common-core` was NOT modified.** Same
outcome as Groups 2–5.

---

## 21. Group 1 Regression — **PASS**
farm `GET /api/v1/farm` 200 · production `GET /api/v1/productions` 200.

## 22. Group 2 Regression — **PASS**
customer list authz 401/403/200 · inventory 200 · customer→farm loopback intact.

## 23. Group 3 Regression — **PASS**
subscription 200 · order 200 · cart 200 · order→customer→farm + order→inventory loopbacks.

## 24. Group 4 Regression — **PASS**
payment 200 · delivery 200 · payment→order loopback · DELIVERY_MANAGER payment-detail gap
preserved (test-asserted).

## 25. Group 5 Regression — **PASS**
notification `/me` 200 · invoice `/me` 200 · **invoice generate → 201 with real `customerName`**
(order + payment + customer loopbacks still authenticate via `X-Internal-Auth`) · invoice PDF
valid · 3 Kafka factories stopped · forged `X-Internal-Auth` → 401.

---

## 26. Docker Results — **PASS WITH CONDITIONS**

- Docker available: **YES** (29.6.2).
- `backend/app/Dockerfile` — **JVM flags changed this group** (§28.6): `MaxRAMPercentage` 70 → 55,
  added `-Xss512k`. Netty arena pins + SerialGC retained.
- Image `farm2home-app:g6` built: **532 MB** (disk; ~unchanged — +91 KB of thin lib jars).
- **512 MB run (`--memory=512m --memory-swap=512m`, DB via `host.docker.internal`):**
  - All 12 services → 200 in-container.
  - Full security matrix (dashboard + reports) correct.
  - Dashboard 8-way fan-out → 200 with real data.
  - Reports: 7 report types 200; CSV/Excel/PDF exports valid at 4, 2,508 and 6,008 rows.
  - Groups 1–5 regression incl. invoice-generate loopback → 201.
  - 10 Flyway schemas; **3** Kafka listener factories stopped; **0** `AccessDeniedException` /
    "response already committed" (async-dispatch fix).
  - **OOMKilled: false**, Running, 0 restarts, health 200 throughout, **0** OOM / GC-overhead
    markers — **with the tuned flags**. On the untuned `MaxRAMPercentage=70` config the JDK/NMT
    diagnostic variant **was OOM-killed** (exit 137) under the same workload, and the production
    JRE image climbed to ~510.8 MiB (99.8 %) and would OOM under real concurrent load.
- Container + image removed after the run.

---

## 27. Memory Comparison — **WARNING** (mandatory)

Measured in the 512 MB container. "G6 workload" = dashboard smoke ×10, reports normal ×20, large
CSV+Excel+PDF ×3, large Excel ×12, then 4-way-parallel (dashboard + 2 reports + Excel export)
×20; dataset 2,508 rows unless noted.

| | Group 5 (10 svc, post-fix) | **Group 6 (12 svc, tuned `RAMPct=55 -Xss512k`)** | Δ |
|---|---|---|---|
| Image size (disk) | ~532 MB | ~532 MB | +0 |
| Startup (`Started Application in`) | ~9–11 s | **~9.6 s** | ~0 |
| **Startup steady** (`docker stats`) | ~355 MiB | **~355 MiB** | **+0** |
| Loaded classes at startup | 22,049 | **22,086** | +37 |
| Metaspace committed at startup | ~92 MB | ~92 MB | +0 |
| Live heap (tenured, post-GC) at startup | ~71 MB | ~72 MB | +1 |
| After dashboard smoke (B) | — | ~402 MiB | |
| After reports normal (C) | — | ~404 MiB | |
| After small export (D) | — | ~429 MiB | |
| After large export CSV+XLSX+PDF (E, 2,508 rows) | — | ~445 MiB (VmHWM 477) | |
| After 12× large Excel (F) | — | ~456 MiB | |
| **After sustained 4-way parallel ×20 (G)** | ~434 MiB (its own workload) | **~482 MiB / RSS 511 / VmHWM 512** | **+48 MiB** |
| Idle settle after G | ~434 MiB | **~482 MiB** (stable, no climb) | |
| Worst case: 5 concurrent XLSX + 3 concurrent PDF | — | **~494 MiB** | |
| Same workload, **6,008-row** dataset | — | **~503–505 MiB** | +70 MiB |
| Same workload, **untuned `RAMPct=70`** | — | **~510.8 MiB (climbs, JDK/NMT variant OOM-killed)** | |
| OOMKilled (tuned) | false | **false** | — |

**Analysis — where the +48–70 MiB goes (it is 100 % workload, ~0 structural):**
1. **Large PDF export** (`PdfTabularExporter`, non-streaming) — ~300-page `PDDocument` resident
   until `save()`; ~15–25 MiB per concurrent large PDF. The single biggest lever.
2. **Concurrent SXSSF Excel + dashboard 8-way fan-out** — up to ~32 concurrent in-process
   loopback requests + N SXSSF window buffers; transient heap that **SerialGC commits and does
   not return**.
3. **SerialGC never uncommits** — once the heap grows to serve a burst it stays committed; the
   `RAMPct=55` ceiling (~281 MB, still ~3× the ~90 MB live heap) is what caps the ratchet.
4. dashboard + reports code/beans/classes: **~+37 classes, +18 beans, ~+1 MiB** — negligible.

**~48–70 MiB for two BFF services is disproportionate** — it is the *export workload*, not the
services. `MaxRAMPercentage=55` was **required** (not optional) — the default config OOMs.

---

## 28. New Discoveries

1. **`StreamingResponseBody` + stateless security → async-dispatch `AccessDeniedException`.**
   The JWT filter set the context only in the holder (no repository save); the MVC async
   re-dispatch re-ran `AuthorizationFilter` with an empty context. Client got the correct file;
   server logged an ERROR per export. **Fixed** — `shouldNotFilterAsyncDispatch()` → `false`
   (one method, no rule change). **PASS.**
2. **`common-export.PdfTabularExporter` is fundamentally non-streaming** (PDF xref table). For a
   6,008-row / ~300-page export it holds the whole `PDDocument` in memory — the dominant
   large-export memory cost. `ExcelTabularExporter` (SXSSF) and `CsvTabularExporter` **are**
   streamed. Shared code, not changed. **WARNING → §30.**
3. **512 MB is genuinely insufficient for 12 services + large in-memory exports** on the default
   `MaxRAMPercentage=70`. Required `RAMPct=55 -Xss512k`; even then ~94–99 %. **WARNING.**
4. **dashboard/reports add zero new dependencies** — `common-export` (POI + PDFBox) was already
   on the classpath from Group 2 (customer-service) / Group 5 (invoice-service). `app.jar` +91 KB.
   **PASS.**
5. **`TestRestTemplate` cannot read the vendor xlsx media type as `byte[]`** — the export test
   uses raw `HttpURLConnection`. Test-harness only. **PASS (documented).**
6. **Test-data pitfall**: `order_type` must be a valid `OrderType` enum (`SUBSCRIPTION` /
   `ONE_TIME`) — an invalid value makes order-service's report query 500. Not an aggregation
   issue. **PASS (documented).**

---

## 29. Blockers

- **512 MB memory headroom — WARNING bordering BLOCKER.** No FAIL item; the aggregate is
  functionally complete and does not OOM with the applied tuning. But peak is **94–99 %** of
  512 MB under realistic large-export load, RSS transiently exceeds the nominal limit, and the
  untuned config OOMs. **This is a blocker for Group 7** (§31) and a strong caution for
  production on a 512 MB instance.

---

## 30. Known Limitations — **DEFERRED**

- **Large PDF export memory** — `PdfTabularExporter` builds the full document in memory
  (~300 pages for 6k rows). Mitigations (deferred, need their own cycle): cap export row count,
  or add a streaming/paginated PDF writer to `common-export`, or offload large PDF exports.
- **512 MB fit** — verified only with `RAMPct=55 -Xss512k` and peaks at 94–99 %. A 13th service,
  higher export concurrency, or a larger dataset needs a **≥ 1 GB instance**.
- Event-driven flows (Kafka) for all prior groups remain dormant — unchanged.
- Aggregate audit `service_name` uniform ("farm2home-spike") — Phase-10 `audit` schema, deferred.
- Business scheduler OFF in tests (property) — prod ON, 1 instance, no ShedLock.
- Dashboard graceful degradation means a downstream outage silently returns fallback zeros — by
  design, unchanged from standalone.

---

## 31. Recommendation for Group 7

**Do NOT start Group 7 (auth-service) on a 512 MB instance.**

- The 12-service aggregate already peaks at **94–99 % of 512 MB** under large-export load, only
  survives with `MaxRAMPercentage=55`, and OOMs on the default config.
- auth-service is **not** a lightweight BFF — it has JPA (users, roles, refresh tokens, OTP),
  Flyway migrations, its own `SecurityConfig`, BCrypt, and JWT *minting*. Expect **+15–25 MiB
  structural** (entities, repositories, another schema's Flyway, BCrypt work factor buffers) on
  top of a footprint that has no room.
- auth also needs a dedicated resolver branch: `GET /api/v1/auth/me` takes
  `@AuthenticationPrincipal User` (a JPA **entity**, not the `(UUID,String,Set)` record) — the
  `ServiceUserPrincipalArgumentResolver` would need a special case.
- Auth's `/api/v1/auth/**` public endpoints (register/login/refresh/otp/google) must be added to
  the one `SecurityFilterChain`'s permit list — the first genuinely new public surface.

**Options for Group 7:**
1. **Move the aggregate to a ≥ 1 GB Render instance** before embedding auth (recommended — 13
   services in one JVM at ~90 MB live heap + ~115 MB metaspace + ~60 MB code + Netty + export
   spikes is a legitimate > 512 MB workload; forcing it under 512 MB trades reliability for $0).
2. **Keep auth as a separate small service** (it is already deployable standalone) and embed only
   1–12; the frontend calls auth directly for login and the aggregate for everything else.
3. If 512 MB is non-negotiable: first reduce the large-export memory ceiling (§30) and
   re-measure, then decide.

Do not begin Group 7 until this Group 6 memory finding is reviewed and an instance-size decision
is made.

---

*Working tree uncommitted on `spike/render-single-jvm`. Group-6 changes: `backend/app/pom.xml`
(+2 lib deps), `Application.java` (+6 scan packages), `application.yml` (`dashboard.*` +
header), `Dockerfile` (JVM flags), `security/SpikeJwtAuthenticationFilter.java`
(`shouldNotFilterAsyncDispatch` override — async export security fix),
`src/test/.../SpikeSingleJvmTests.java` (38 → 42 tests). **No** change to `SpikeJpaConfig`,
`SpikeFlywayConfig`, `SpikeSecurityConfig`, any service module, any migration SQL, any
`common-*`, or `backend/pom.xml`.*
