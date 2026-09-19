# Farm2Home Group 2 Aggregation Report

Branch: `spike/render-single-jvm` · 2026-09-06 · **Nothing committed or pushed.**
Scope: customer-service + inventory-service only. Groups 3–7 not touched.

---

## 1. Status

**PASS**

`backend/app` now embeds **farm, production, customer, inventory** in one servlet JVM.
20/20 aggregation tests green. Verified live (local jar + 512 MB Docker container). Group 1
regression intact.

---

## 2. Services Added

- **customer-service** — customers, addresses, DPDP consent/data-rights, India location master
- **inventory-service** — inventory items, products, product categories, reviews, stock transactions

---

## 3. Packaging

Unchanged from Phase 2. `customer-service` / `inventory-service` added to `app/pom.xml` as
`<classifier>lib</classifier>` dependencies. Verified:

| | customer-service | inventory-service |
|---|---|---|
| main `*.jar` still executable (`Start-Class`) | ✅ | ✅ |
| thin `*-lib.jar` produced | ✅ | ✅ |
| `-lib.jar` contains `application*.yml` / `db/migration/**` / `logback-spring.xml` | **0** (none) | **0** (none) |

`app/target/app.jar` — executable (`Start-Class: com.farm2home.app.Application`), bundles all
four service `-lib.jars`, exactly **one** `application.yml` (the app's own). Standalone service
packaging and the 15 per-service Dockerfiles are untouched.

---

## 4. Component Scan

`Application.java` — `@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class, scanBasePackages = {…})`:

**Scanned** (`com.farm2home.app` + per service):

| Service | Packages scanned |
|---|---|
| farm | `.controller` `.service` `.mapper` |
| production | `.controller` `.service` `.mapper` |
| customer | `.controller` `.service` `.mapper` `.client` `.kafka` |
| inventory | `.controller` `.service` `.mapper` `.client` `.kafka` |

**Never scanned** — the whole `config` package of every service. Specifically excluded and
replaced app-side:

| Excluded class (per service) | Replaced by |
|---|---|
| `config.SecurityConfig` (`securityFilterChain`/`filterChain`) | `app.security.SpikeSecurityConfig` — one chain |
| `config.JpaConfig` (`@EnableJpaRepositories` — do not compose) | `app.config.SpikeJpaConfig` — one `@EnableJpaRepositories` |
| `config.AuditorAwareImpl` (`@Component("auditorAwareImpl")`) | `app.config.SpikeAuditorAware` — one `auditorAwareImpl` |
| `config.WebClientConfig` (`loadBalancedWebClientBuilder`) | `app.config.LoadBalancerClientConfig` — one builder |
| `config.GatewayHeaderAuthFilter` | dropped — `app.security.SpikeJwtAuthenticationFilter` is the only authenticator |
| `config.UserPrincipal` (per-service record) | resolved by `app.security.ServiceUserPrincipalArgumentResolver` (§6) |
| `config.OpenApiConfig` | dropped (springdoc still self-configures) |
| `kafka.KafkaConfig` (`@Configuration` bean `kafkaConfig` — collides ×N) | scanned, but coexists via the FQN bean-name generator; `autoStartup` forced off (§9) |

`FullyQualifiedAnnotationBeanNameGenerator`: names every scanned component by FQN so the two
`kafka.KafkaConfig` classes (and every future same-named `@Configuration`) don't collide on
`kafkaConfig`. Verified safe — no code in the codebase looks a scanned bean up by simple name
(`@Qualifier("x")` / `getBean("x")` / `@Resource(name=)` / `@DependsOn` — none exist; all
wiring is by-type). `@Bean`-method names are unaffected by this generator, and `auditorAwareImpl`
is bound with an explicit `@Bean(name=…)`.

---

## 5. Security

**SecurityFilterChain count: 1** (test-asserted). `@EnableWebSecurity` / `@EnableMethodSecurity`
exist once. Method-level `@PreAuthorize` on the scanned controllers still enforces every
per-endpoint rule.

**Consolidated public set** (`SpikeSecurityConfig`) = `ApiConstants.PUBLIC_ENDPOINTS_BASE` +
`/uploads/**` + `POST /api/v1/data-rights-requests/submit` (added this group, from customer's
own SecurityConfig). All match the authorization matrix in `AGGREGATION_REPORT.md §6`.

### Verified (live + tests) — customer

| Request | Expected | Result |
|---|---|---|
| `GET /api/v1/customers` no JWT | 401 | ✅ |
| `GET /api/v1/customers` bad / refresh JWT | 401 | ✅ |
| `GET /api/v1/customers` + forged `X-User-Roles: SUPER_ADMIN` (no JWT) | 401 | ✅ |
| `GET /api/v1/customers` CUSTOMER / FARM_MANAGER | 403 | ✅ |
| `GET /api/v1/customers` SUPER_ADMIN / DELIVERY_MANAGER | 200 | ✅ (matrix: `hasAnyAuthority(SUPER_ADMIN, DELIVERY_MANAGER)`) |
| `GET /api/v1/customers/{id}` SUPER_ADMIN | 200 | ✅ |
| `GET /api/v1/customers/{id}` CUSTOMER, non-owner | 404 (not 403) | ✅ (ownership shape preserved) |
| `GET /api/v1/customers/me/consents` CUSTOMER | 200 | ✅ (principal resolver) |
| `GET /api/v1/locations/states` authenticated | 200 | ✅ |
| `GET /api/v1/data-rights-requests` no JWT | 401 | ✅ (still SUPER_ADMIN) |
| `POST /api/v1/data-rights-requests/submit` no JWT | reaches handler (not 401) | ✅ (public) |

### Verified (live + tests) — inventory

| Request | Expected | Result |
|---|---|---|
| `GET /api/v1/inventory/products` authenticated | 200 | ✅ (`isAuthenticated()`) |
| `GET /api/v1/inventory/product-categories` authenticated | 200 | ✅ |
| `POST /api/v1/inventory/product-categories` CUSTOMER | 403 | ✅ (`hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)`) |
| `POST /api/v1/inventory/product-categories` FARM_MANAGER | 201 | ✅ |
| `GET /api/v1/inventory/summary` CUSTOMER | 403 | ✅ (`hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)`) |
| `GET /api/v1/inventory/summary` FARM_MANAGER / DELIVERY_MANAGER | 200 | ✅ |
| `GET /api/v1/reviews/my` CUSTOMER | 200 | ✅ (`hasAnyAuthority(CUSTOMER)` + principal resolver) |
| `GET /api/v1/reviews/my` FARM_MANAGER | 403 | ✅ |

### JWT / forged-header regression (Group 1 + Group 2)

Missing → 401 · bad → 401 · refresh (`type != ACCESS`) → 401 · valid ACCESS → authenticated ·
wrong role → 403 · correct role → success · forged `X-User-*` with no JWT → 401 (the JWT filter
strips gateway-owned headers). Both `hasAnyRole(...)` (farm) and `hasAnyAuthority(...)`
(customer, inventory, production) enforced under the one chain — the filter grants both `X` and
`ROLE_X` per role.

### @AuthenticationPrincipal compatibility

**~20 call sites** across customer (`CustomerController`, `ConsentController`,
`DataRightsRequestController`, `DeliveryAvailabilityServiceImpl`) and inventory
(`ReviewController`) inject `@AuthenticationPrincipal <svc>.config.UserPrincipal`. All 12
service `UserPrincipal` records have the identical canonical constructor
`(UUID userId, String mobile, Set<String> roles)`.

`ServiceUserPrincipalArgumentResolver` (registered ahead of the built-ins by
`WebMvcArgumentResolverConfig`'s `RequestMappingHandlerAdapter` `BeanPostProcessor`) detects any
parameter whose type is `com.farm2home.*.config.UserPrincipal` and reflectively invokes that
record's `(UUID, String, Set)` constructor from the JWT-derived `SpikePrincipal`. It requires
exactly that constructor and fails loudly if a future service deviates. **No controller or
service code changed.** All authorization inputs preserved — userId, mobile, and the full role
set (which drives each record's `isAdmin()` / `isDeliveryPartner()`).

---

## 6. JPA

- **EntityManagerFactory count: 1** (test-asserted)
- One `@EnableJpaRepositories` (`SpikeJpaConfig`) over: `farm.domain.repository`,
  `production.domain.repository`, `customer.domain.repository`, `inventory.domain.repository`,
  `common.core.audit`
- Repository beans discovered exactly once (test-asserted): `farmRepository`, `cowRepository`,
  `businessSettingsRepository`, `milkProductionRepository`, `customerRepository`,
  `customerAddressRepository`, `inventoryItemRepository`, `productRepository`, `reviewRepository`,
  + one `AuditLogRepository`
- Duplicate repositories: **none**. `spring.main.allow-bean-definition-overriding` is **not** set.

---

## 7. Flyway

`spring.flyway.enabled=false`; `SpikeFlywayConfig` runs **one Flyway per schema**, each from a
distinct `classpath:db/migration/<schema>` (byte-for-byte copies of each service's own SQL —
28 files total; checksums unchanged so they validate against the existing histories).

| Schema | Flyway bean | Versioned migrations applied | History isolated? |
|---|---|---|---|
| farm | `farmFlyway` | 7 | ✅ only farm descriptions |
| production | `productionFlyway` | 2 | ✅ only production descriptions |
| customer | `customerFlyway` | 7 | ✅ only customer descriptions |
| inventory | `inventoryFlyway` | 7 | ✅ only inventory descriptions |

No V1/V2 collisions, no checksum errors, no cross-schema contamination (each
`<schema>.flyway_schema_history` contains only its own migration descriptions — verified by
SQL). Row counts identical before/after every run. Migration SQL semantics unchanged.

**Audit-log routing:** `hibernate.default_schema=farm`, so the shared unqualified `AuditLog`
entity resolves there — customer/inventory audit rows (like farm/production) land in
`farm.audit_log` with `service_name = "farm2home-spike"`; each service's own
`<schema>.audit_log` table stays intact but unused (identical to the Group 1 behaviour). A
dedicated `audit` schema + per-service tagging is the P1-1 Phase-10 decision, deliberately
deferred — this group changes nothing about audit vs. Group 1.

---

## 8. REST / Load Balancing

`spring-cloud-loadbalancer` and Eureka are **off**. `LoadBalancerClientConfig` supplies the one
`loadBalancedWebClientBuilder` (bean name kept) with an `ExchangeFilterFunction` that rewrites
`lb://<anything>/<path>` → `http://localhost:<this app's port>/<path>` — a real loopback HTTP
call back into this JVM's own dispatcher. The caller's bearer token rides along via
`RequestHeaderForwarder` (see §15), which is what authenticates the loopback.

| Caller → `lb://` target | Used by | Group 2 outcome |
|---|---|---|
| customer → `lb://farm-service` (`/api/v1/farm/business-settings`) | `DeliveryAvailabilityServiceImpl` | ✅ **resolves in-process** — `GET /api/v1/customers/{id}/delivery-availability` returns 200 (verified: loopback request logged as `user-agent=ReactorNetty` from 127.0.0.1 → 200) |
| customer → `lb://delivery-service` (`/api/v1/delivery/routes/search`) | `DeliveryRouteSelectionServiceImpl` | ⚠ delivery-service not embedded → loopback 404 → the client already catches `WebClientException` and returns `Optional.empty()`, so `delivery-availability` still returns 200 with `routeId: null`. **Graceful.** Full function returns with Group 4. |
| inventory → `lb://customer-service` | `ReviewServiceImpl` | ✅ resolves in-process (customer embedded) |
| inventory → `lb://order-service` (purchase check) | `ReviewServiceImpl` | ⚠ order-service not embedded → loopback 404, caught by the client. Review creation's "has this customer ordered this product" check degrades. Full function returns with Group 3. |

No Eureka required, no external service URLs, no routing loop, no request leaves the process.
(Rejected alternative: re-enabling `spring-cloud-loadbalancer` + static `SimpleDiscoveryClient`
instances — on spring-cloud `2023.0.0` it mis-reconstructs the `lb://` scheme, producing
`lb://localhost:8099`, and adds ~25 `BeanPostProcessorChecker` startup warnings.)

---

## 9. Kafka

- **Broker dependency: none.** Startup does not touch a broker.
- **Consumers:** customer's `CustomerEventConsumer` (`@KafkaListener`) — its container is created
  and wired but **not started**. `KafkaDisabledConfig` (a `BeanPostProcessor`) forces
  `autoStartup=false` on every `AbstractKafkaListenerContainerFactory` — needed because the
  services use custom-named factories that Boot's `spring.kafka.listener.auto-startup=false`
  doesn't reach. Test-asserted: `KafkaListenerEndpointRegistry` reports every container stopped.
- **Producers:** inventory's `InventoryEventProducer` (`KafkaTemplate`) — the bean is lazy;
  no connection at startup. A `send()` would only occur on a mutating request / the scheduled
  low-stock sweep, and its future completes exceptionally (logged via the existing
  `.whenComplete`) — non-fatal.
- **`farm2home.kafka.enabled` does not exist** anywhere in the codebase; the config is via
  `spring.kafka.listener.auto-startup=false` (app profile) + the BPP.
- The existing per-service Kafka architecture is **unmodified**.

**Event functionality unavailable in this deployment** (until the in-process event bridge
phase): auto-provisioning a `customer.customers` row from auth-service's `CUSTOMER_CREATED`
(customer consumer), and low-stock / stock-change `INVENTORY_UPDATED` notifications (inventory
producer → notification-service). Synchronous REST paths (incl. the future order→inventory stock
decrement) are unaffected.

---

## 10. Endpoint Verification (actual endpoints, actual results)

| Service | Endpoint | Auth | HTTP |
|---|---|---|---|
| — | `GET /actuator/health` | none | 200 (`{"status":"UP"}`) |
| farm | `GET /api/v1/farm` | FARM_MANAGER | 200 |
| farm | `POST /api/v1/farm` (valid body) | CUSTOMER | 403 |
| production | `GET /api/v1/productions` | FARM_MANAGER | 200 |
| production | `GET /api/v1/productions/summary/today` | CUSTOMER / DELIVERY_MANAGER | 403 / 200 |
| customer | `GET /api/v1/customers` | none / CUSTOMER / SUPER_ADMIN | 401 / 403 / 200 |
| customer | `GET /api/v1/customers/{id}` | SUPER_ADMIN | 200 |
| customer | `GET /api/v1/customers/me/consents` | CUSTOMER | 200 |
| customer | `GET /api/v1/customers/{id}/delivery-availability` | SUPER_ADMIN | 200 (in-process loopback to farm) |
| customer | `GET /api/v1/locations/states` | CUSTOMER | 200 |
| inventory | `GET /api/v1/inventory/products` | CUSTOMER | 200 |
| inventory | `GET /api/v1/inventory/summary` | CUSTOMER / FARM_MANAGER | 403 / 200 |
| inventory | `POST /api/v1/inventory/product-categories` | CUSTOMER / FARM_MANAGER | 403 / 201 |
| inventory | `GET /api/v1/reviews/my` | CUSTOMER / FARM_MANAGER | 200 / 403 |

Also verified identically inside the 512 MB Docker container (health, farm, production, customer,
inventory, 401/403).

---

## 11. Database Verification

- Schemas tested independently: **farm, production, customer, inventory** — each with its own
  `flyway_schema_history` (7 / 2 / 7 / 7 versioned rows, all `success`, 0 failed).
- Tables verified present: `farm.cows`, `farm.farms`, `production.milk_production`,
  `customer.customers`, `customer.consent_records`, `customer.data_rights_requests`,
  `inventory.products`, `inventory.reviews`, `inventory.product_categories`.
- No cross-schema contamination (each history contains only its own migration descriptions —
  asserted by SQL and by tests).
- Repository queries hit the right schema — verified: a `FARM_MANAGER` `POST` created a row in
  `inventory.product_categories` (then cleaned up).
- `audit_log`: aggregate writes land in `farm.audit_log` (see §7). `customer.audit_log` /
  `inventory.audit_log` untouched. Not a silent change — documented, and identical to Group 1.
- All verification test data cleaned; row counts back to baseline.

---

## 12. Group 1 Regression

- **Farm: PASS** — `GET /api/v1/farm` 200, `POST` role restriction 403, migrations intact
- **Production: PASS** — `GET /api/v1/productions` 200, `summary/today` role restriction 403/200
- All 10 original spike assertions retained (now part of the 20-test suite) and green.

---

## 13. Tests

`mvn -pl app verify` (app module; deps pre-installed): **BUILD SUCCESS**

```
Tests run: 20   Passed: 20   Failed: 0   Errors: 0   Skipped: 0
```

`mvn -pl app -am verify` (full reactor): **BUILD FAILURE at common-core** —
`EndToEndWorkflowIntegrationTest` → *"Could not find a valid Docker environment"*. This is
**pre-existing and unrelated** (documented Testcontainers/Docker-socket issue on this Windows
host; it's in `common-core`, not `app`; Testcontainers can't attach even though the Docker CLI
works). Not caused by this aggregation. The JaCoCo coverage gate is **not** touched (disabled
for the `app` module only; every real module's gate is intact).

New tests (Group 2): `noDuplicateInfrastructureBeans`, `oneFlywayInstancePerSchema` (4),
`allRepositoriesDiscoveredExactlyOnce`, `customer/inventorySchemaHasItsOwnCompleteMigrationHistory`,
`forgedGatewayIdentityHeadersAreIgnored`, `validAccessTokenReaches…InventoryInOneProcess`,
`serviceUserPrincipalArgumentResolverAdaptsPerServiceType`,
`customer/inventoryRoleRestrictionsPreserved`, `customerToFarmCallResolvesAsInProcessLoopback`,
`kafkaListenerContainersDoNotAutoStart`.

---

## 14. Docker

- Available: **YES** (Docker 29.6.2 — came back up since the spike)
- `backend/app/Dockerfile` created (`eclipse-temurin:21-jre-alpine`, `COPY target/app.jar`,
  `ENTRYPOINT java -jar app.jar`, binds `${PORT}`). Image built: 190 MB content.
- **512 MB run: PASS** — `docker run --memory=512m --memory-swap=512m`:

| Metric | Value |
|---|---|
| Startup (`Started Application in`) | **18.98 s** |
| Readiness (`startupTimeMs`) | 19 099 ms |
| Ready (wall clock incl. container start) | ~22.5 s |
| Flyway (4 schemas) | all applied, "up to date" |
| Memory (`docker stats`, steady + under light load) | **338 MiB / 512 MiB (66 %)** |
| OOMKilled | **false** · container stayed **Running** |
| OOM / GC-overhead in logs | none |

Endpoints in-container: health, farm, production, customer, inventory all 200; 401/403 correct.

---

## 15. New Issues Discovered

1. **`lb://` + `SimpleDiscoveryClient` mis-reconstructs the scheme** on spring-cloud `2023.0.0`
   (`lb://farm-service` → `lb://localhost:8099`, connection fails). Worked around with the
   in-process rewrite filter (§8). If Path B ever needs real LB, upgrade spring-cloud first.
2. **In-process `lb://` loopback needs the caller's token.** `RequestHeaderForwarder` forwarded
   `X-User-*` (gateway model) but not `Authorization`; the aggregate has no header-trust, so the
   loopback got 401. **Fix (shared, minimal, backward-compatible):**
   `common-web/RequestHeaderForwarder.forwardable()` now also forwards the `Authorization`
   header. In the microservice deployment this is a no-op (services authenticate from `X-User-*`;
   the gateway already passes `Authorization` through). common-web tests: 13/13 green.
3. **Custom-named Kafka listener factories ignore `spring.kafka.listener.auto-startup`** — needed
   the `KafkaDisabledConfig` BPP (§9).
4. **Aggregate audit `service_name` is uniform** ("farm2home-spike") — forensic granularity by
   `service_name` alone is lost in the aggregate (`entity_type` / `request_uri` still
   distinguish). Fixed properly by the Phase-10 `audit` schema decision.
5. `HttpMessageNotReadableException` (malformed JSON / bad enum value) → 500 not 400. This is
   **pre-existing** shared `GlobalExceptionHandler` behaviour (same standalone), not an
   aggregation issue.

---

## 16. Overall Architecture Status

| | |
|---|---|
| Group 1 (farm, production) | **PASS** |
| Group 2 (customer, inventory) | **PASS** |
| Full aggregation (13 services) | **NOT DONE** (4/13 embedded) |
| Render | **NOT READY** (no deploy; but the 512 MB memory constraint is now **verified** for the current 4-service footprint) |

---

## 17. Next Step

**Stop and review before implementing Group 3.**

Group 3 = subscription-service + order-service. It is a separate implementation cycle and
carries new risk: order-service has 2 Kafka consumers + a `@Scheduled` daily-order-generation
job, `lb://` calls to customer/inventory/production (customer + production now resolve
in-process; inventory does too), and `subscription`/`order` share the commerce-core ownership
model. Do not begin it until this Group 2 change is reviewed.

---

*Working tree left uncommitted on `spike/render-single-jvm`. P1-1 diff:
`backend/pom.xml` (Phase 2), `backend/app/` (Groups 1–2),
`common-observability/ObservabilityAutoConfiguration.java` (spike),
`common-web/RequestHeaderForwarder.java` (Group 2, item 15.2). The pre-existing ` M` on
`RequestHeaderForwarderTest.java` was already in the tree before this work.*
