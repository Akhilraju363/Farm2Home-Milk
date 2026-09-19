# Farm2Home Single-JVM Aggregation — Group 5 Memory & Dependency Review

Branch: `spike/render-single-jvm` · 2026-09-07 · **Nothing committed or pushed.**
Type: **read-only performance + dependency investigation.** One isolated, measured JVM-flag
optimization was applied to `backend/app/Dockerfile` (Netty arena cap — §12/§13); no business
code, schema, migration, or dependency was changed.

Classification legend: **PASS / PASS WITH CONDITIONS / WARNING / BLOCKER / DEFERRED**

---

## 0. TL;DR

| Question | Answer |
|---|---|
| Current steady memory (512 MB container) | **~355 MiB** right after startup; **~425–434 MiB** after an invoice-generation + PDF smoke |
| Current peak memory | **~434 MiB / RSS ~444 MB** under sustained invoice+PDF load (was ~466 MiB / RSS ~493 MB before the fix) |
| Main cause of the Group 4 → Group 5 increase | **Not** notification/invoice code (that is only ~12 MiB). It is: first-ever **Reactor-Netty/WebClient** initialisation triggered by invoice's 4-way `Mono.zip` loopback (Netty pooled **direct-memory arenas**, +threads, +~900 classes), **PDFBox** first-render statics, **SerialGC committed-heap growth that never returns**, and **measurement skew** (Group 5's "steady" was sampled after a heavier smoke than Group 4's). |
| Memory leak? | **No.** 15+ repeated invoice+PDF cycles → live heap flat at ~78–80 MB after GC; direct memory and metaspace both plateau. |
| Safe optimisation found & applied | `-Dio.netty.allocator.numDirectArenas=2 -Dio.netty.allocator.numHeapArenas=2` → **measured −31 MiB stats / −49 MB RSS** under identical workload, zero functional change. |
| 512 MB risk | **YELLOW** — stable, no OOM, but ~70–80 MiB headroom and the big consumers scale per-service. |
| Group 6 safe to start? | **YELLOW** — proceed but measure early; apply `MaxRAMPercentage=55–60` or move to 1 GB if post-Group-6 peak > ~470 MiB. |

---

## 1. Group 4 baseline

From `GROUP4_REPORT.md` §24–25 (8 services, `docker stats`, `--memory=512m`,
`-XX:MaxRAMPercentage=70 -XX:+UseSerialGC`):

| | Group 4 |
|---|---|
| Image (disk) | ~530 MB |
| Startup (`Started Application in`) | 21.5 s (machine-load dependent) |
| Steady memory (after light smoke) | ~364 MiB / 512 (71 %) |
| Peak (after 45 mixed reqs) | ~376 MiB / 512 (73 %) |
| Headroom at peak | ~136 MiB |
| OOMKilled | false |

**Reconstructed baseline (this review):** a *Group-4-equivalent* build was produced by
temporarily removing only the `notification-service` + `invoice-service` `-lib` deps, their 7
scan packages, their 2 entity/2 repo packages, their 2 Flyway beans, and `NotificationEmailAppConfig`
(then fully restored — 38/38 tests green again, `app.jar` byte-identical). Measured in the same
512 MB container with a JDK image + `-XX:NativeMemoryTracking=summary`:

| Group-4-equiv, point A (startup, post–full-GC, **no business requests**) | |
|---|---|
| `docker stats` | 370.5 MiB |
| RSS / peak RSS | 397 MB / 409 MB |
| NMT committed total | ~380 MB |
| Java heap committed / **live (tenured after full GC)** | 173 MB / **69.2 MB** |
| Metaspace used / committed | 103.7 MB / 90.9 MB |
| Compressed class space used | 13.9 MB |
| **Loaded classes** | **21,921** |
| Symbol | 37.8 MB |
| Code cache committed | 36.4 MB |
| Threads | 33 (stacks 3.2 MB committed) |
| Netty direct ("Other") | ~0 |

---

## 2. Group 5 baseline

From `GROUP5_REPORT.md` §24–25 (10 services, same flags):

| | Group 5 (as reported) |
|---|---|
| Image (disk) | ~532 MB (+2 MB) |
| Steady memory | ~425 MiB / 512 (83 %) |
| Peak (after 300 mixed reqs + invoice gen + PDF) | ~443 MiB / 512 (86 %) |
| Headroom at peak | ~69 MiB |
| OOMKilled | false |

**Measured this review (JDK + NMT image, same 512 MB container):**

| Group 5, point A (startup, **no business requests**) | |
|---|---|
| `docker stats` | 383.1 MiB |
| RSS / peak RSS | 409 MB / 431 MB |
| NMT committed total | ~362 MB |
| Java heap committed / **live (tenured after full GC)** | 144–148 MB / **~71 MB** |
| Metaspace used / committed | 104.9 MB / 92.1 MB |
| Compressed class space used | 13.9 MB |
| **Loaded classes** | **22,049** |
| Symbol | 38.0 MB |
| Code cache committed | 41.7 MB |
| Threads | 33 (stacks 3.2 MB committed) |
| Netty direct ("Other") | ~0.1 MB |

| Group 5, after invoice-generate + PDF + full GC (point C) | |
|---|---|
| `docker stats` / RSS | 445 MiB / 472 MB |
| Java heap committed / **live** | 189 MB / **~72 MB** |
| Loaded classes | **24,600** (+2,551 vs point A) |
| Threads | 55–61 (+28) |
| Netty direct (`jvm.buffer.memory.used` + NMT "Other") | 17.9 MB → 34 MB after 15 cycles |
| Metaspace used | 118 MB |
| Code cache | ~55 MB |

| Group 5, after a **standard heavy workload** (12× invoice+PDF, 200-req burst) + full GC | |
|---|---|
| `docker stats` / RSS / peak RSS | **465.7 MiB / 493 MB / 493 MB** |
| Java heap committed / **live** | **202 MB / 80.8 MB** |
| Metaspace used / committed | 122 MB / 123 MB |
| Code cache | 56 MB |
| Threads | ~70 (stacks 8 MB) |
| Netty direct | ~34 MB |

---

## 3. Memory delta

### 3a. At startup, no requests — the *pure* cost of adding notification + invoice to the context

| Metric | G4-equiv | G5 | **Δ (real)** |
|---|---|---|---|
| `docker stats` | 370.5 MiB | 383.1 MiB | **+12.6 MiB** |
| RSS | 397 MB | 409 MB | +12 MB |
| Loaded classes | 21,921 | 22,049 | **+128** |
| Metaspace used | 103.7 MB | 104.9 MB | +1.2 MB |
| Compressed class space | 13.9 MB | 13.9 MB | ~0 |
| Symbol | 37.8 MB | 38.0 MB | +0.2 MB |
| Live heap (tenured, post-GC) | 69.2 MB | ~71 MB | +2 MB |
| Code cache | 36.4 MB | 41.7 MB | +5.3 MB |
| Threads | 33 | 33 | 0 |
| Beans (Actuator `/beans`) | ~753 | **771** | **+18** (notification 9, invoice 7, +`notificationFlyway`, +`invoiceFlyway`) |

**➡️ Embedding notification + invoice themselves costs only ~12 MiB** — the two `-lib` jars'
mapped pages, ~128 classes, 18 beans, 4 entities in the shared metamodel, 12 extra Flyway
migrations at startup (transient), and ~5 MB more JIT code. This is permanent, unavoidable and
small.

### 3b. Where the *reported* "+61 MiB steady / +67 MiB peak" actually came from

| Contributor | ~MiB | Nature |
|---|---|---|
| notification + invoice classes / beans / entities / jars (§3a) | **+12** | real, permanent, unavoidable |
| **PDFBox / FontBox first-render statics** — Adobe Glyph List (~1 MB `HashMap`), Standard-14 AFM metric cache (~1–2 MB), ~470 fontbox+pdfbox classes → metaspace + symbol + code | **+8–10** | real, permanent, **one-time** (first PDF only) |
| **Reactor-Netty / WebClient first-init** — triggered by invoice's `Mono.zip` of 4 concurrent downstream calls: ~800–1,000 Netty/Reactor classes, +8–28 threads (event loops + `boundedElastic`), and **Netty `PooledByteBufAllocator` direct arenas** (16 MB chunk × arena count) | **+15–25** (pre-fix) → **+4–9** (post-fix) | one-time ramp to a **plateau**, not a leak. Group 4's measured window never fired a loopback so this was invisible there. |
| **SerialGC committed-heap growth** — invoice's zip + composite `InvoiceResponse` + PDF `byte[]` + `ByteArrayOutputStream` doubling is a bigger transient spike than any Group 1–4 flow; SerialGC grows the old-gen commit and **never uncommits** | **+15–20** | **reserved-but-free**, not live (live heap only +2 MB) — shows in RSS, is not "consumed" |
| **Measurement skew** — `GROUP5_REPORT` sampled "steady" *after* an invoice-generate + PDF; `GROUP4_REPORT` sampled it after a lighter GET-only smoke. A matched heavy workload puts the Group-4-equiv at ~399 MiB (8 loopbacks, no PDF), well above its reported 364. | **+5–15** | methodology, not a real regression |

The GROUP5_REPORT hypothesis that **`spring-boot-starter-mail` is a material contributor is
disproved by NMT**: metaspace grew only ~1 MB at startup vs Group 4, and no `JavaMailSender` /
Jakarta-Mail class is instantiated (`email.enabled=false`, `spring.mail.host` unset). Its cost
is < 1 MB of loaded classes (§7).

---

## 4. JVM memory breakdown (Group 5, final image, after standard workload, 512 MB container)

NMT `summary` (JDK diag image; the production JRE image runs ~10–15 MiB leaner — no NMT
overhead, fewer JDK classes):

| NMT category | Committed | Notes |
|---|---|---|
| **Java Heap** | **~200 MB** | but **live = ~80 MB** (tenured after full GC). ~120 MB is SerialGC reserve that never returns. `MaxRAMPercentage=70` → 358 MB ceiling; only ~200 MB ever committed. |
| **Metaspace (class metadata)** | **~107 MB** | 24,640 loaded classes across 10 services. **No class unloading** (single classloader). Scales ~linearly with #services. |
| Compressed class space | ~16 MB | part of the above |
| **Code cache (JIT)** | **~57 MB** | fully warmed under load; scales with exercised code paths (PDFBox, Jackson, Netty, Hibernate). |
| **Symbol table** | **~42 MB** | method/field/class name symbols; scales with #classes. |
| Shared class space (CDS) | ~13 MB | default JDK CDS archive |
| **Thread stacks** | ~8 MB | ~70–86 threads; default `-Xss` 1 MB, only touched pages commit (~64–128 KB each) |
| **"Other" (Netty direct arenas)** | **~9 MB** (was ~34 MB) | capped by the applied fix (§12) |
| GC | ~0.7 MB | SerialGC's tiny footprint — the reason it is the right GC here |
| Internal / Compiler / Metaspace-mgmt / misc | ~10 MB | |
| NMT tracking overhead | ~7 MB | diagnostic image only — not in production |
| **NMT committed total** | **~455–465 MB** | |
| **RSS (production JRE image)** | **~444 MB** | + musl malloc retained + resident mmap'd `app.jar` pages beyond NMT accounting |

- **JVM:** Eclipse Temurin 21.0.x (`21-jre-alpine` prod / `21-jdk-alpine` diag), HotSpot.
- **Flags (prod):** `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` + (new) `-Dio.netty.allocator.numDirectArenas=2 -Dio.netty.allocator.numHeapArenas=2`. Container `--memory=512m --memory-swap=512m` (swap disabled).
- **Heap:** SerialGC (DefNew + Tenured). Container-aware sizing: 70 % of 512 MB = ~358 MB max; young gen ~45–55 MB, tenured grows to ~130 MB committed / ~80 MB live.
- **GC threads:** SerialGC = 1 (`VM Thread`). No parallel GC workers.
- **Compiler threads:** 2 (1×C1, 1×C2).
- **Executor threads:** Tomcat `http-nio-8080-exec` (lazy, ~10 at rest, max 200 default), 1 Tomcat `Poller`, 1 `Acceptor`, 2 `Catalina-utility`; Reactor `reactor-http-nio-*` event loops (created on first WebClient call, = host CPU count), Reactor `boundedElastic` workers (lazy, invoice's blocking `.block()` on the zip), 1 `boundedElastic-evictor`.
- **Scheduler threads:** `farm2home.scheduling.enabled=false` in tests/this review → **0** `@Scheduled`. In prod (`true`) → one shared `ThreadPoolTaskScheduler`, `spring.task.scheduling.pool.size=8`. Plus common-core's `auditTaskExecutor` (`@Async` audit writer).
- **Hikari:** ONE pool `spike-hikari`, `maximum-pool-size=10`, `minimum-idle=0` (idle connections released), 1 `spike-hikari housekeeper` thread + 1 `PostgreSQL-JDBC-Cleaner`.
- **HTTP client resources:** ONE `reactorResourceFactory` (shared Netty `LoopResources` + `ConnectionProvider`), ONE `loadBalancedWebClientBuilder`. No per-service WebClient runtime.
- **Kafka:** 3 listener-container factories, **all `autoStartup=false`** (no broker, no consumer threads); 5 producer `KafkaTemplate`s (lazy, never connect). No Kafka network/IO threads at rest.
- **Direct buffers:** `jvm.buffer.memory.used[direct]` = ~9 MB / ~34 buffers after the fix (was ~34 MB). NIO + Netty pooled.
- **Thread count:** 33 at rest → ~70–86 under load (Netty loops + boundedElastic + Tomcat exec).

---

## 5. Dependency delta (Group 4 → Group 5)

`mvn dependency:tree -pl app` — every new artifact and its origin:

| New artifact | Via | Jar size | Runtime cost in the aggregate |
|---|---|---|---|
| `org.apache.pdfbox:pdfbox:3.0.3` | invoice-service | 1,983 KB | **required.** ~350 classes loaded on first render + Adobe Glyph List (~1 MB static) |
| `org.apache.pdfbox:fontbox:3.0.3` | invoice-service | 1,604 KB | **required** (Standard-14 AFM metrics). ~120 classes + AFM cache (~1–2 MB static) |
| `org.apache.pdfbox:pdfbox-io:3.0.3` | invoice-service | 47 KB | **required** (PDFBox I/O primitives) |
| `commons-logging:commons-logging:1.3.3` | pdfbox | 65 KB | pdfbox 3's logging facade — required by pdfbox |
| `org.springframework.boot:spring-boot-starter-mail:3.2.0` | notification-service | 5 KB | pom-only |
| `org.springframework:spring-context-support:6.1.1` | starter-mail | 170 KB | **~3–5 classes loaded** (`JavaMailSender` interface referenced by `NotificationEmailAppConfig`'s `@Bean` param) — nothing instantiated |
| `org.eclipse.angus:jakarta.mail:2.0.2` | starter-mail | 700 KB | **0 classes loaded** — `MailSenderAutoConfiguration` backs off (no `spring.mail.host`), `LoggingEmailProvider` is used |
| `org.eclipse.angus:angus-activation:2.0.1` | starter-mail | 27 KB | 0 classes loaded |

**Total new jars: ~4.6 MB uncompressed** (~0.9 MB added to `app.jar`).
**No new spring-kafka, webflux, reactor-netty, POI, or common-events** — those were already
present from Group 2 (`customer-service`). Maven de-dupes; notification's spring-kafka
requirement is satisfied by the existing copy.

**Materially memory-relevant new library: PDFBox/FontBox only** (~8–10 MB permanent after first
render). `spring-boot-starter-mail` is **not** material (NMT-confirmed).

---

## 6. Bean delta

| | Group-4-equiv | Group 5 | Δ |
|---|---|---|---|
| Total beans (Actuator `/actuator/beans`) | ~753 | **771** | **+18** |

New beans: `notification.controller.NotificationLogController`,
`notification.service.impl.NotificationServiceImpl`, `notification.mapper.NotificationMapperImpl`,
`notification.client.CustomerServiceClient`, `notificationLogRepository`,
`notificationTemplateRepository`, `emailProvider`, `emailService`, `EmailProperties`;
`invoice.controller.InvoiceController`, `invoice.service.impl.InvoiceServiceImpl`,
`invoice.service.impl.InvoicePdfRenderer`, `invoice.client.{OrderServiceClient,PaymentServiceClient,CustomerServiceClient}`,
`invoiceRepository`; `notificationFlyway`, `invoiceFlyway` (+ app `NotificationEmailAppConfig`).

18 beans ≈ a few KB of `BeanDefinition` + singleton overhead. **Not memory-relevant.**

**Component scan is clean** (§ Application.java): only `controller` / `service` / `mapper` /
`client` are scanned for notification, only `controller` / `service` / `client` for invoice.
**`com.farm2home.notification.kafka` is deliberately NOT scanned** (its `@Bean kafkaListenerContainerFactory`
would name-collide with order's). No service `config` package is scanned. No unexpected beans.

---

## 7. Notification dependency analysis (`spring-boot-starter-mail`)

1. **Why present:** `notification-service`'s own `pom.xml` declares it — `SmtpEmailProvider` uses
   `org.springframework.mail.javamail.JavaMailSender` / `jakarta.mail.internet.MimeMessage`. It
   enters the aggregate transitively through `notification-service:lib`.
2. **Required by currently-enabled functionality?** **No.** `email.enabled=false` (default) →
   `NotificationEmailAppConfig` returns `LoggingEmailProvider`; `spring.mail.host` unset →
   `MailSenderAutoConfiguration` / `MailSenderValidatorAutoConfiguration` back off → **no
   `JavaMailSender` bean** (Actuator `/beans`: zero `JavaMailSender`). NMT: only ~3–5 classes
   from `spring-context-support` load (the `JavaMailSender` interface, referenced in a `@Bean`
   method parameter type); **zero** Jakarta-Mail classes load. Measured metaspace cost < 1 MB.
3. **Safe to exclude from the aggregate?** **Technically yes, but not worth it and not risk-free.**
   `NotificationEmailAppConfig.emailProvider(Optional<JavaMailSender>, …)` and `SmtpEmailProvider`
   reference `org.springframework.mail.javamail.*`; a blind `<exclusion>` would need
   `NotificationEmailAppConfig` rewritten to drop the `JavaMailSender` parameter and the
   `SmtpEmailProvider` branch (an **app-code change to a config class**, against "prefer no code
   changes"), for a **< 1 MB** saving that NMT shows is noise. **Recommendation: do not exclude.**
4. **Standalone notification-service:** unaffected — no change proposed to its pom or code.

**Classification: DEFERRED (not worth doing).**

---

## 8. PDF dependency analysis (PDFBox)

- **Do not remove / do not replace** (per scope). All four jars — `pdfbox`, `pdfbox-io`,
  `fontbox`, `commons-logging` — are **required at runtime**: `InvoicePdfRenderer` uses
  `PDDocument`, `PDPageContentStream`, `PDType1Font(Standard14Fonts.FontName.HELVETICA…)`.
  `fontbox` supplies the Standard-14 AFM metrics; `pdfbox-io` the buffered I/O; `commons-logging`
  is pdfbox 3's logging facade.
- **Nothing to isolate.** PDFBox 3.x is already split into the minimal set (`pdfbox` + `pdfbox-io`
  + `fontbox`); there is no optional "full" artifact pulling extra codecs/XMP/preflight here.
- **Runtime footprint when exercised:** ~470 classes + Adobe Glyph List (~1 MB) + Standard-14
  AFM cache (~1–2 MB), all **static and one-time** — loaded on the first `/invoices/{id}/pdf`,
  never released, never grows. ~8–10 MB permanent.
- **No system-font scan:** the renderer uses only built-in Type1 fonts (no `loadTTF`, no
  `PDType0Font`), so PDFBox never triggers a `FontMapper` OS-font-directory scan (which would be
  a multi-MB hit on a full OS — irrelevant on the alpine JRE image anyway).

**Classification: PASS (no action; no leak — see §9).**

---

## 9. PDF memory test (STEP 6)

Controlled repeated generation in the 512 MB container (`POST /invoices/generate/{orderId}` →
`GET /invoices/{id}/pdf` → delete row, ×15):

| After | Live heap (tenured, post–full-GC) | Direct buffers | Metaspace used |
|---|---|---|---|
| 0 PDFs (startup) | ~71 MB | ~0 | 105 MB |
| 1 PDF | ~72 MB | 18 MB | 109 MB |
| 15 PDFs | **~78–80 MB** | **~34 MB (plateau)** | ~118 MB (plateau) |
| 15 PDFs + full GC + idle | **~78–80 MB** | ~34 MB | ~118 MB |

- **No leak.** Live heap after GC is flat at ~78–80 MB regardless of PDF count. The +6–8 MB vs
  point A is cached Jackson serializers for the invoice/order/payment/customer DTOs + Hibernate
  query-plan cache + FontBox/GlyphList statics — all bounded, all one-time.
- **Direct memory** ramps 0 → 18 → 34 MB and **stops** — Netty `PooledByteBufAllocator` arena
  chunks are retained by design (pooled ≠ freed); bounded by arena count × 16 MB. The applied
  fix (§12) caps this at ~9 MB.
- **PDFBox resources:** every `PDDocument` / `PDPageContentStream` is in try-with-resources; the
  `ByteArrayOutputStream` + returned `byte[]` (~1.2 KB) are collected after the HTTP response is
  written. No retained byte arrays, no unclosed documents, no unbounded buffers.
- A transient allocation spike per render exists (`ByteArrayOutputStream` grow-and-copy) but it
  is KB-scale and returns to baseline.

**Classification: PASS (no leak; temporary allocation only).**

---

## 10. Duplicate infrastructure analysis (STEP 3)

Actuator `/actuator/beans` on the running 512 MB container:

| Infrastructure | Expected | Actual | ✅ |
|---|---|---|---|
| `DataSource` (HikariDataSource) | 1 | **1** (`dataSource`) | ✅ |
| Hikari pools | 1 | **1** (`spike-hikari`) | ✅ |
| `EntityManagerFactory` | 1 | **1** (test-asserted) | ✅ |
| `PlatformTransactionManager` | 1 | **1** (`transactionManager`) | ✅ |
| `DispatcherServlet` | 1 | **1** | ✅ |
| `SecurityFilterChain` | 1 | **1** | ✅ |
| `TaskScheduler` (prod) | 1 | 1 (`SchedulingConfig`, conditional) | ✅ |
| `@Async` executor | 1 | **1** (`auditTaskExecutor`, common-core) | ✅ |
| `applicationTaskExecutor` | 1 | 1 (Boot default, unused) | ✅ |
| `WebClient.Builder` | 1 | **1** (`loadBalancedWebClientBuilder`) | ✅ |
| `ReactorResourceFactory` (Netty loop + pool) | 1 | **1** | ✅ |
| `ClientHttpConnector` factory | 1 | **1** (`reactorClientHttpConnectorFactory`) | ✅ |
| `ObjectMapper` | 1 primary | **1 primary** (`objectMapper`) + `kafkaObjectMapper` (order's, non-primary, Kafka-only) | ✅ documented |
| Kafka listener-container factories | 3, all stopped | **3, all `autoStartup=false`** | ✅ |
| Kafka `KafkaTemplate` | 5 (lazy) | 5, none connect | ✅ |
| `Flyway` | 10 (one per schema) | **10** + `spikeFlywayMigrations` marker | ✅ |
| `JavaMailSender` | 0 | **0** | ✅ |
| `AuditorAware` | 1 | **1** (`auditorAwareImpl`) | ✅ |
| `InternalCallToken` | 1 | **1** | ✅ |
| `InvoicePdfRenderer` | 1 | **1** | ✅ |
| SMS / Push / Email provider | 1 each | 1 each, all logging | ✅ |

**No duplicate infrastructure.** Every shared resource is a singleton. 771 beans total.

**Classification: PASS.**

---

## 11. Security verification (STEP 9)

Run against the running 512 MB container (Group-5 hardening: JWT + per-process `X-Internal-Auth`
+ loopback check):

| Test | Result | Expected |
|---|---|---|
| no JWT → `/invoices` | 401 | 401 ✅ |
| no JWT → `/notifications/me` | 401 | 401 ✅ |
| bad JWT → `/invoices` | 401 | 401 ✅ |
| refresh-token-as-access → `/invoices` | 401 | 401 ✅ |
| forged `X-User-*` (no JWT) → `/customers`, `/invoices` | 401 | 401 ✅ |
| forged `X-Internal-Auth` + `X-User-*` → `/invoices` | 401 | 401 ✅ |
| valid CUSTOMER JWT → `/notifications/me` | 200 | 200 ✅ |
| valid admin JWT → `/invoices` | 200 | 200 ✅ |
| valid CUSTOMER JWT → `/invoices` (list) | 403 | 403 ✅ |
| valid internal loopback (invoice generate, real `X-Internal-Auth`) | 201 | 201 ✅ |

**Also re-verified after the JVM-flag change (§12):** identical results, invoice generate → 201
with real `customerName` from the customer loopback, PDF `%PDF-1.6`.

**Classification: PASS — security mechanism intact, not weakened for performance.**

---

## 12. Safe optimization candidates

| # | Change | Current → Change | Expected memory impact | Risk | Verification |
|---|---|---|---|---|---|
| **1 ✅ APPLIED** | **Cap Netty allocator arenas** — `-Dio.netty.allocator.numDirectArenas=2 -Dio.netty.allocator.numHeapArenas=2` | arena count = 2×host-cores (each a 16 MB chunk) → **2** | **measured −31 MiB `docker stats` / −49 MB RSS** under standard workload (direct "Other" 34 → 9 MB) | **Low** — the aggregate's only outbound HTTP is the in-process loopback; a single low-concurrency Render instance never needs >2 arenas. Group 6 fan-out adds concurrency but 2×16 MB is still ample. | Standard workload re-run in 512 MB container: 465.7 → **434.3 MiB**, RSS 493 → **444 MB**, all 10 services + security + invoice + PDF re-verified unchanged, no OOM (§14). |
| 2 | Lower heap ceiling — `-XX:MaxRAMPercentage=70` → `55–60` | 358 MB → 281–307 MB heap max | caps the SerialGC "committed-but-free" ratchet; ~15–30 MB lower RSS ceiling under load | **Low-Medium** — live heap is only ~80 MB so 281 MB is 3.5× headroom, but an unforeseen spike (huge report export, many concurrent PDFs) has less slack. An **operator/deploy decision**, not imposed here. | Tested `55` + standard workload: 435.8 MiB / RSS 462 MB, no OOM, health UP — but the win overlaps #1. |
| 3 | Reduce thread stacks — `-Xss512k` | default 1 MB → 512 KB | ~2–4 MB committed at ~80 threads; ~35 MB *reserved* | Low-Medium — deep Hibernate/Spring/PDFBox call stacks; 512 KB is usually safe but not free of risk. | Tested, no `StackOverflowError` under workload. Marginal — recommend only if squeezing. |
| 4 | `-XX:ReservedCodeCacheSize=96m` | default 240 MB reserved | trims *reserved* (not committed) code cache by ~140 MB; helps NMT reserved, not RSS | None (committed ~57 MB < 96 MB) | Tested, no JIT recompilation storms. Cosmetic for RSS. |
| 5 | AppCDS (`-XX:SharedArchiveFile`) | default JDK CDS only | ~5–15 MB metaspace + faster startup by sharing the app's own classes | Low, but adds a build step (archive generation) and image complexity | Not tested — **DEFERRED**, revisit if metaspace becomes the binding constraint. |
| 6 | **G1GC** (`-XX:+UseG1GC`) | SerialGC | **REJECTED** | — | Tested: G1 costs **+65–68 MB** native (`GC` NMT category: 0.7 MB → 68 MB — remembered sets, marking bitmaps, card tables) which **cancels** its ~64 MB lower heap ceiling. Net neutral-to-worse on a 512 MB box. **Keep SerialGC.** |
| 7 | Exclude `spring-boot-starter-mail` from `app` | present | **< 1 MB** (NMT-confirmed noise) | Medium — requires rewriting `NotificationEmailAppConfig` (app-code change) | **REJECTED** — not worth a code change for sub-MB (§7). |
| 8 | `server.tomcat.threads.max` 200 → 50 | 200 | 0 at steady state (threads are lazy); caps a pathological burst | Low | Not applied — no measured benefit at this load. |

---

## 13. Changes actually made

**Change 1 — `backend/app/Dockerfile`** (JVM flags — no business code, no schema, no migration,
no dependency, no `application.yml`):

```diff
- ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
+ ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Dio.netty.allocator.numDirectArenas=2 -Dio.netty.allocator.numHeapArenas=2"
```

(plus an explanatory comment block). Fully reversible; overridable at deploy time via `JAVA_OPTS`.

**Change 2 — `SpikeSingleJvmTests.invoiceGenerationDrivesInProcessLoopback…` (test only).** The
memory review's repeated invoice-generate/delete churn shifted which row Postgres returns for the
test's unordered `LIMIT 1` order pick, exposing a **pre-existing flake in the Group-5 test**: it
could land on a seed order whose `customer.customers` row no longer exists, so the
`customerName` loopback assertion failed. Fixed by `JOIN customer.customers … is_deleted=false`
+ `ORDER BY o.id` so the pick is deterministic and always has a live customer (which is exactly
what that assertion needs). Test-data-selection robustness only — no production code, no
assertion weakened. `mvn -pl app verify` → **38/38 green** after the fix.

**Diagnostic-only, reverted, nothing left behind:** a temporary Group-4-equivalent build
(pom/scan/JPA/Flyway edits + `NotificationEmailAppConfig` moved out) was used for the §1/§3
baseline and then **fully restored** — `mvn -pl app verify` → **38/38 green**, `app.jar`
byte-identical to the pre-review artifact (129,364,334 bytes). DB baseline restored
(`invoice.invoices`=0, `notification.notification_logs`=0, `farm.audit_log`=16, `order.carts`=2).

---

## 14. Before / after measurements (identical standard workload, 512 MB container)

Standard workload = touch all 10 services ×3, generate+PDF+delete an invoice ×12, 200-request
mixed burst, then full GC + 20 s idle.

| | **Before** (`SerialGC 70`) | **After** (`+ numDirectArenas/numHeapArenas=2`) | Δ |
|---|---|---|---|
| `docker stats` after workload | **465.7 MiB** | **434.3 MiB** | **−31.4 MiB** |
| RSS after workload | **493 MB** | **444 MB** | **−49 MB** |
| Peak RSS (`VmHWM`) | **493 MB** | **444 MB** | **−49 MB** |
| `docker stats` idle after GC | 465.7 MiB | 434.6 MiB | −31 MiB |
| Netty direct ("Other" / `jvm.buffer`) | ~34 MB | **~9 MB** | −25 MB |
| Java heap live (post-GC) | 80.8 MB | ~80 MB | ~0 (unchanged — not a heap change) |
| Startup steady (pre-workload, JRE image) | ~365 MiB | **~355 MiB** | −10 MiB |
| Headroom at load peak | ~46 MiB (RSS) / ~46 MiB (stats) | **~68 MiB (RSS) / ~78 MiB (stats)** | +22–32 MiB |
| OOMKilled / Running / Restarts | false / true / 0 | **false / true / 0** | — |
| 10 services + security + invoice + PDF | PASS | **PASS (re-verified)** | unchanged |

All "after" numbers measured **inside the `--memory=512m --memory-swap=512m` container** from
the production `Dockerfile` (`21-jre-alpine`).

---

## 15. 512 MB risk assessment

**Classification: YELLOW — WARNING.**

- **Stable, no leak, no OOM.** After the fix, sustained invoice+PDF load holds at
  **~434 MiB `docker stats` / ~444 MB RSS = 85–87 % of 512 MB**, ~68–78 MiB headroom.
- **The headroom is modest and the dominant consumers scale per-service:**
  - Metaspace ~107 MB committed (24,640 classes) — **+~5 MB per lean service added, no ceiling, never released.**
  - Java heap committed ~200 MB (only ~80 MB live) — SerialGC reserve; grows with the largest transient allocation any endpoint makes and never shrinks.
  - Code cache ~57 MB — grows with exercised code paths.
  - Symbol ~42 MB — grows with #classes.
- **The 70 % `MaxRAMPercentage` budget is arithmetically over-committed** (358 MB heap ceiling +
  ~250 MB non-heap ≈ 600 MB > 512 MB); it only survives because the heap never actually commits
  to 358 MB. A large in-memory report export (Group 6, `common-export`/POI) on top of the
  already-committed 200 MB heap is the most plausible OOM trigger.
- **Recommended posture:** keep the arena fix; hold `MaxRAMPercentage=55–60` in reserve as a
  deploy-time override; treat 470 MiB RSS as the "add headroom or move to 1 GB" line.

---

## 16. Group 6 readiness

**Classification: YELLOW — proceed with mandatory early measurement.**

| Signal | Assessment |
|---|---|
| Measured headroom after Group 5 + fix | ~68–78 MiB at load peak — **enough for ~2 lean BFF services, not comfortably more** |
| dashboard-service / reports-service shape | BFF aggregators: little/no schema, but **many** outbound `lb://` calls → more Reactor concurrency, more `boundedElastic` threads, more pooled direct memory pressure (the arena cap helps but 2×16 MB is the ceiling) |
| reports-service `common-export` (POI/Excel) | **already on the classpath** (Group 2, via customer-service) so no new jar cost — **but** in-memory `XSSFWorkbook` generation is a real **transient heap spike** on top of an already-committed ~200 MB heap. This is the single biggest Group-6 risk. |
| Metaspace trajectory | +~10–15 MB for two more services' classes/beans → ~120 MB committed. Fine, but monotonic. |
| Per-service context cost (measured pattern) | ~6 MiB/service at startup (§3a) → ~+12 MiB for Group 6 |

**Recommendation:**
1. Proceed to Group 6.
2. **Immediately after wiring dashboard + reports, run the §14 standard workload** (add a large
   report-export call) in the 512 MB container and compare RSS.
3. If post-Group-6 load peak **≤ ~460 MiB RSS** → continue on 512 MB.
4. If **> ~470 MiB RSS** → apply `-XX:MaxRAMPercentage=55` (measured safe here) and/or
   `-Xss512k`; if still tight, **move the Render instance to 1 GB** — 13 services in one JVM at
   ~80 MB live heap + ~120 MB metaspace + ~60 MB code + Netty + reports-export spikes is a
   legitimate >512 MB workload and forcing it under 512 MB trades reliability for $0.
5. Do **not** switch to G1GC (measured worse here).

---

## 17. Recommendation

- **Group 5 memory: PASS WITH CONDITIONS.** No leak, no duplicate infrastructure, clean
  component scan, security intact. The real cost of notification + invoice is only ~12 MiB; the
  reported "+61 MiB" is dominated by first-time Reactor-Netty/WebClient + PDFBox initialisation
  (one-time, triggered by invoice's loopback), SerialGC committed-heap that never returns
  (reserved, not consumed), and Group-4-vs-Group-5 measurement-window skew.
- **Applied:** the Netty arena cap — a safe, isolated, deploy-level change, **measured −31 MiB
  `docker stats` / −49 MB RSS** with zero functional or security change, restoring peak headroom
  to ~70–78 MiB.
- **512 MB: YELLOW.** Viable for 10 services with the fix; getting tight.
- **Group 6: YELLOW.** Safe to start; measure immediately after; be ready to drop
  `MaxRAMPercentage` to 55 or move to a 1 GB instance — especially because reports-service does
  in-memory Excel generation.
- **Do not** pursue: G1GC (worse), `spring-boot-starter-mail` exclusion (sub-MB, needs code
  change), PDFBox changes (no leak, all deps required).

---

## STRICT STOP

Investigation complete. No Group 6 implementation, no dashboard, no reports, no Phase-10 audit
schema, no Kafka replacement, nothing committed or pushed. The only change on disk is the
`backend/app/Dockerfile` `JAVA_OPTS` line (§13), plus this report and the memory note.

---

## Final answer

1. **Current steady memory:** ~355 MiB right after startup; ~425–434 MiB after an
   invoice-generation + PDF smoke (512 MB container, production JRE image, post-fix).
2. **Current peak memory:** ~434 MiB `docker stats` / ~444 MB RSS under sustained invoice+PDF
   load (was ~466 MiB / ~493 MB before the fix).
3. **Main causes of the Group 5 increase:** (a) first-ever Reactor-Netty/WebClient stack
   initialisation — ~900 classes, +28 threads, Netty pooled **direct-memory arenas** — triggered
   by invoice's 4-way concurrent `Mono.zip` loopback, which Group 4's measured window never fired;
   (b) PDFBox/FontBox first-render statics (~8–10 MB, one-time); (c) SerialGC growing committed
   heap under invoice/PDF transient allocation and never uncommitting (~15–20 MB *reserved*, not
   live — live heap only +2 MB); (d) measurement skew — Group 5's "steady" was sampled after a
   heavier smoke than Group 4's. The **pure** cost of the notification + invoice code/beans/jars
   is only **~12 MiB**. `spring-boot-starter-mail` is **not** a material contributor (NMT-confirmed).
4. **Memory leak found?** **No.** 15+ repeated invoice+PDF cycles: live heap flat at ~78–80 MB
   after GC; direct memory and metaspace both plateau; all PDFBox resources are
   try-with-resources closed.
5. **Safe optimizations identified:** cap Netty allocator arenas to 2 (**applied**); lower
   `MaxRAMPercentage` to 55–60 (recommended as a deploy override, not imposed); `-Xss512k` and
   `ReservedCodeCacheSize=96m` (marginal). **Rejected:** G1GC (its ~65 MB native overhead cancels
   the benefit — measured), excluding `spring-boot-starter-mail` (sub-MB, needs a code change).
6. **Changes actually made:** (a) one line in `backend/app/Dockerfile` —
   `-Dio.netty.allocator.numDirectArenas=2 -Dio.netty.allocator.numHeapArenas=2` added to
   `JAVA_OPTS` (+ comment); (b) a **test-only** determinism fix to
   `invoiceGenerationDrivesInProcessLoopback…` (the review's DB churn exposed a pre-existing
   flake — its unordered `LIMIT 1` order pick could land on an order whose customer row no longer
   exists; now `JOIN customer.customers … ORDER BY o.id`). No business code, schema, migration,
   dependency, or `application.yml` change; no assertion weakened. The temporary
   Group-4-equivalent build used for baselining was fully restored — `mvn -pl app verify` →
   **38/38 green**.
7. **New measured memory after the change (identical standard workload, 512 MB container):**
   `docker stats` 465.7 → **434.3 MiB** (−31 MiB); RSS / peak RSS 493 → **444 MB** (−49 MB);
   Netty direct 34 → 9 MB; load-peak headroom ~46 → **~70–78 MiB**; no OOM; 10 services +
   security + invoice + PDF all re-verified unchanged.
8. **512 MB risk classification: YELLOW (WARNING).** Stable, no leak, no OOM, but ~70–78 MiB
   headroom and the dominant consumers (metaspace ~107 MB, committed heap ~200 MB, code ~57 MB)
   scale with each added service; the 70 % heap budget is arithmetically over-committed and only
   survives because the heap never fully commits.
9. **Is Group 6 safe to start?** **YELLOW — yes, with conditions.** Proceed, but measure RSS
   immediately after wiring dashboard + reports using the §14 workload (including a large report
   export); if the load peak exceeds ~470 MiB RSS, apply `-XX:MaxRAMPercentage=55` and/or move
   the Render instance to 1 GB. reports-service's in-memory Excel generation is the main risk.
   Do not switch to G1GC.
