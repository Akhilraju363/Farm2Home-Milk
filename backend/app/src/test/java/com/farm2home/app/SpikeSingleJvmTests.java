package com.farm2home.app;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.DispatcherServlet;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Single-JVM aggregation tests. Verifies Groups 1–6 (farm, production, customer, inventory,
 * subscription, order, payment, delivery, notification, invoice, dashboard, reports — 12
 * services) coexist in ONE Spring context / ONE servlet app / ONE {@code SecurityFilterChain} /
 * ONE {@code EntityManagerFactory}, with one isolated Flyway per schema (10 — dashboard/reports
 * own no DB), the JWT auth model enforced (incl. the per-service {@code UserPrincipal}
 * argument-resolver adapter and forged header rejection), in-process {@code lb://} loopback —
 * including invoice-service's order/payment/customer composition + PDF rendering, dashboard's
 * 8-way parallel {@code Mono.zip} fan-out, and reports-service's paged CSV/Excel(SXSSF)/PDF
 * exports — the business scheduler held off, and health UP with no Eureka / Config Server /
 * Kafka broker.
 *
 * <p>Requires the local Postgres the individual services use (Docker/Testcontainers is
 * unavailable here — same constraint as the rest of the project's DB tests).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Group 3: keep the business scheduler OFF for tests — a run straddling 23:00 must not
        // let order's DailyOrderGenerationService write real "order".orders rows. Prod keeps it ON.
        properties = "farm2home.scheduling.enabled=false")
class SpikeSingleJvmTests {

    @Autowired ApplicationContext ctx;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Value("${jwt.secret}") String jwtSecret;

    // ---- context: exactly one of each shared piece of infrastructure --------------------

    @Test
    void oneServletDispatcherOneSecurityChainOneEntityManagerFactory() {
        assertThat(ctx.getBeanNamesForType(DispatcherServlet.class)).hasSize(1);
        assertThat(ctx.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        assertThat(ctx.getBeanNamesForType(EntityManagerFactory.class)).hasSize(1);
    }

    @Test
    void noDuplicateInfrastructureBeans() {
        // one @LoadBalanced-style builder (the 9 service WebClientConfig copies are excluded)
        assertThat(ctx.getBeanNamesForType(WebClient.Builder.class)).hasSize(1);
        // one auditor (the 11 service AuditorAwareImpl copies are excluded)
        assertThat(ctx.getBeanNamesForType(AuditorAware.class)).hasSize(1);
        assertThat(ctx.containsBean("auditorAwareImpl")).isTrue();
        // Eureka client must NOT be active
        assertThat(ctx.getBeanNamesForType(com.netflix.discovery.EurekaClient.class)).isEmpty();
        // Group 3: exactly one MilkPriceProperties (order.config, registered via app
        // OrderPricingConfig @EnableConfigurationProperties — its own @Component is inert here)
        assertThat(ctx.getBeanNamesForType(com.farm2home.order.config.MilkPriceProperties.class)).hasSize(1);
        // Group 3: MVC serialization uses a @Primary ObjectMapper, not order's bare kafkaObjectMapper
        assertThat(ctx.getBean(com.fasterxml.jackson.databind.ObjectMapper.class))
                .isSameAs(ctx.getBean("objectMapper"));
        assertThat(ctx.getBean("objectMapper")).isNotSameAs(ctx.getBean("kafkaObjectMapper"));
        // Group 4: exactly one PaymentGatewayProvider — the mock (no external calls / credentials)
        assertThat(ctx.getBeanNamesForType(com.farm2home.payment.gateway.PaymentGatewayProvider.class)).hasSize(1);
        assertThat(ctx.getBean(com.farm2home.payment.gateway.PaymentGatewayProvider.class))
                .isInstanceOf(com.farm2home.payment.gateway.mock.MockPaymentGatewayProvider.class);
        // Group 4: payment.event package NOT scanned -> no dead @Async Kafka publisher / executor
        assertThat(ctx.containsBean("paymentEventExecutor")).isFalse();
        // Group 5: exactly one proof-of-origin token for in-process lb:// system-identity calls
        assertThat(ctx.getBeanNamesForType(com.farm2home.app.security.InternalCallToken.class)).hasSize(1);
        // Group 5: notification email layer defaults to the logging provider — no SMTP, no JavaMailSender
        assertThat(ctx.getBean("emailProvider"))
                .isInstanceOf(com.farm2home.notification.email.LoggingEmailProvider.class);
        assertThat(ctx.getBeanNamesForType(org.springframework.mail.javamail.JavaMailSender.class)).isEmpty();
        // Group 6: dashboard + reports are pure BFF — their controller/service/client beans are
        // scanned, but they contribute NO repository, NO Flyway, NO Kafka, NO ObjectMapper.
        assertThat(ctx.getBeanNamesForType(com.farm2home.dashboard.controller.DashboardController.class)).hasSize(1);
        assertThat(ctx.getBeanNamesForType(com.farm2home.reports.controller.ReportController.class)).hasSize(1);
        assertThat(ctx.getBeanNamesForType(com.farm2home.dashboard.service.DashboardService.class)).hasSize(1);
        assertThat(ctx.getBeanNamesForType(com.farm2home.reports.service.ReportService.class)).hasSize(1);
        // dashboard/reports own no persistence — no JPA repositories from either package
        assertThat(ctx.getBeanNamesForType(org.springframework.data.repository.Repository.class))
                .noneMatch(n -> n.contains("dashboard") || n.contains("reports"));
    }

    @Test
    void businessSchedulerIsOffForTestsButWiredForProd() {
        // farm2home.scheduling.enabled=false (test property) => SchedulingConfig conditionally absent
        assertThat(ctx.getBeanNamesForType(com.farm2home.app.config.SchedulingConfig.class)).isEmpty();
        // no @Scheduled task is registered, so order's 23:00 daily-order-generation cannot fire
        var registrar = ctx.getBeanProvider(
                org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).getIfAvailable();
        if (registrar != null) {
            assertThat(registrar.getScheduledTasks()).isEmpty();
        }
    }

    @Test
    void oneFlywayInstancePerSchema() {
        assertThat(ctx.getBeanNamesForType(Flyway.class))
                .containsExactlyInAnyOrder("farmFlyway", "productionFlyway", "customerFlyway",
                        "inventoryFlyway", "subscriptionFlyway", "orderFlyway", "paymentFlyway", "deliveryFlyway",
                        "notificationFlyway", "invoiceFlyway");
    }

    @Test
    void allRepositoriesDiscoveredExactlyOnce() {
        // Group 1
        assertThat(ctx.containsBean("farmRepository")).isTrue();
        assertThat(ctx.containsBean("cowRepository")).isTrue();
        assertThat(ctx.containsBean("businessSettingsRepository")).isTrue();
        assertThat(ctx.containsBean("milkProductionRepository")).isTrue();
        // Group 2
        assertThat(ctx.containsBean("customerRepository")).isTrue();
        assertThat(ctx.containsBean("customerAddressRepository")).isTrue();
        assertThat(ctx.containsBean("inventoryItemRepository")).isTrue();
        assertThat(ctx.containsBean("productRepository")).isTrue();
        assertThat(ctx.containsBean("reviewRepository")).isTrue();
        // Group 3
        assertThat(ctx.containsBean("subscriptionRepository")).isTrue();
        assertThat(ctx.containsBean("orderRepository")).isTrue();
        assertThat(ctx.containsBean("cartRepository")).isTrue();
        assertThat(ctx.containsBean("orderItemRepository")).isTrue();
        assertThat(ctx.containsBean("subscriptionSnapshotRepository")).isTrue();
        // Group 4
        assertThat(ctx.containsBean("paymentRepository")).isTrue();
        assertThat(ctx.containsBean("walletRepository")).isTrue();
        assertThat(ctx.containsBean("walletTransactionRepository")).isTrue();
        assertThat(ctx.containsBean("deliveryAssignmentRepository")).isTrue();
        assertThat(ctx.containsBean("deliveryPartnerRepository")).isTrue();
        assertThat(ctx.containsBean("deliveryRouteRepository")).isTrue();
        assertThat(ctx.containsBean("deliveryLocationRepository")).isTrue();
        // Group 5
        assertThat(ctx.containsBean("notificationLogRepository")).isTrue();
        assertThat(ctx.containsBean("notificationTemplateRepository")).isTrue();
        assertThat(ctx.containsBean("invoiceRepository")).isTrue();
        // shared audit repository — exactly one
        assertThat(ctx.getBeanNamesForType(com.farm2home.common.core.audit.AuditLogRepository.class))
                .hasSize(1);
    }

    @Test
    void servletStackNotReactive() {
        assertThat(ctx.getEnvironment().getProperty("spring.main.web-application-type", ""))
                .isNotEqualTo("reactive");
        assertThat(ctx.getBeanNamesForType(
                org.springframework.boot.web.servlet.server.ServletWebServerFactory.class)).hasSize(1);
    }

    // ---- database: one isolated Flyway history per schema, no collisions ----------------

    @Test
    void farmSchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("farm")).isEqualTo(7);
        assertThat(descriptions("farm"))
                .contains("init farm schema", "create farms table", "create business settings")
                .doesNotContain("init production schema", "init customer schema", "init inventory schema");
        assertThat(tableExists("farm", "cows")).isTrue();
        assertThat(tableExists("farm", "farms")).isTrue();
    }

    @Test
    void productionSchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("production")).isEqualTo(2);
        assertThat(descriptions("production")).contains("init production schema").doesNotContain("create farms table");
        assertThat(tableExists("production", "milk_production")).isTrue();
    }

    @Test
    void customerSchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("customer")).isEqualTo(7);
        assertThat(descriptions("customer"))
                .contains("init customer schema", "create dpdp consent and rights requests")
                .doesNotContain("init inventory schema", "init farm schema");
        assertThat(tableExists("customer", "customers")).isTrue();
        assertThat(tableExists("customer", "consent_records")).isTrue();
    }

    @Test
    void inventorySchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("inventory")).isEqualTo(7);
        assertThat(descriptions("inventory"))
                .contains("init inventory schema", "create products table", "create reviews table")
                .doesNotContain("init customer schema", "init farm schema");
        assertThat(tableExists("inventory", "products")).isTrue();
        assertThat(tableExists("inventory", "reviews")).isTrue();
    }

    @Test
    void subscriptionSchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("subscription")).isEqualTo(2);
        assertThat(descriptions("subscription")).contains("init subscription schema")
                .doesNotContain("init order schema", "init customer schema");
        assertThat(tableExists("subscription", "subscriptions")).isTrue();
    }

    @Test
    void orderSchemaHasItsOwnCompleteMigrationHistory() {
        // "order" is a reserved word — Flyway/Hibernate quote it; the schema still isolates cleanly
        assertThat(appliedMigrations("order")).isEqualTo(6);
        assertThat(descriptions("order"))
                .contains("init order schema", "create cart", "add delivery route id")
                .doesNotContain("init subscription schema", "init inventory schema");
        assertThat(tableExists("order", "orders")).isTrue();
        assertThat(tableExists("order", "carts")).isTrue();
        assertThat(tableExists("order", "subscription_snapshots")).isTrue();
    }

    @Test
    void paymentSchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("payment")).isEqualTo(4);
        assertThat(descriptions("payment"))
                .contains("init payment schema", "add payment gateway columns")
                .doesNotContain("init delivery schema", "init order schema");
        assertThat(tableExists("payment", "payments")).isTrue();
        assertThat(tableExists("payment", "wallets")).isTrue();
        assertThat(tableExists("payment", "wallet_transactions")).isTrue();
    }

    @Test
    void deliverySchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("delivery")).isEqualTo(8);
        assertThat(descriptions("delivery"))
                .contains("init delivery schema", "create delivery locations", "add auto assigned flag")
                .doesNotContain("init payment schema", "init order schema");
        assertThat(tableExists("delivery", "delivery_assignments")).isTrue();
        assertThat(tableExists("delivery", "delivery_partners")).isTrue();
        assertThat(tableExists("delivery", "delivery_routes")).isTrue();
    }

    @Test
    void notificationSchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("notification")).isEqualTo(10);
        assertThat(descriptions("notification"))
                .contains("init notification schema", "html email templates", "push notification templates")
                .doesNotContain("init invoice schema", "init order schema");
        assertThat(tableExists("notification", "notification_logs")).isTrue();
        assertThat(tableExists("notification", "notification_templates")).isTrue();
    }

    @Test
    void invoiceSchemaHasItsOwnCompleteMigrationHistory() {
        assertThat(appliedMigrations("invoice")).isEqualTo(2);
        assertThat(descriptions("invoice"))
                .contains("init invoice schema", "create audit log")
                .doesNotContain("init notification schema", "init payment schema");
        assertThat(tableExists("invoice", "invoices")).isTrue();
    }

    // ---- infrastructure independence ---------------------------------------------------

    @Test
    void healthIsPublicAndUpWithoutEurekaConfigServerOrKafka() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }

    // ---- security: JWT model + per-service UserPrincipal adapter ------------------------

    @Test
    void protectedEndpointsRejectMissingBadAndRefreshTokens() {
        for (String path : List.of("/api/v1/farm", "/api/v1/customers", "/api/v1/inventory/products",
                "/api/v1/subscriptions", "/api/v1/orders", "/api/v1/cart",
                "/api/v1/payments", "/api/v1/wallets/me", "/api/v1/delivery/assignments", "/api/v1/delivery/routes")) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode())
                    .as("no token -> 401 for %s", path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(get(path, "not.a.jwt").getStatusCode())
                    .as("bad token -> 401 for %s", path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(get(path, token("REFRESH", List.of())).getStatusCode())
                    .as("refresh token -> 401 for %s", path).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void forgedGatewayIdentityHeadersAreIgnored() {
        HttpHeaders forged = new HttpHeaders();
        forged.add("X-User-Id", "11111111-1111-1111-1111-111111111111");
        forged.add("X-User-Mobile", "9999999999");
        forged.add("X-User-Roles", "SUPER_ADMIN");
        for (String path : List.of("/api/v1/customers", "/api/v1/subscriptions", "/api/v1/orders",
                "/api/v1/payments", "/api/v1/delivery/assignments", "/api/v1/invoices",
                "/api/v1/notifications/summary", "/api/v1/dashboard/summary", "/api/v1/reports/sales")) {
            ResponseEntity<String> resp = rest.exchange(path, HttpMethod.GET,
                    new HttpEntity<>(forged), String.class);
            assertThat(resp.getStatusCode()).as("forged headers, no JWT -> 401 for %s", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void forgedInternalAuthSecretDoesNotUnlockSystemIdentity() {
        // The X-User-* system-identity branch is gated on the per-process X-Internal-Auth secret
        // (a client cannot know it) AND a loopback peer. A guessed secret must still 401 — the
        // TestRestTemplate call originates from loopback, so this isolates the secret check.
        HttpHeaders forged = new HttpHeaders();
        forged.add("X-User-Id", "00000000-0000-0000-0000-000000000000");
        forged.add("X-User-Mobile", "invoice-service");
        forged.add("X-User-Roles", "SUPER_ADMIN");
        forged.add("X-Internal-Auth", "not-the-real-secret");
        for (String path : List.of("/api/v1/customers", "/api/v1/invoices", "/api/v1/orders",
                "/api/v1/dashboard/summary", "/api/v1/reports/sales")) {
            assertThat(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(forged), String.class).getStatusCode())
                    .as("forged X-Internal-Auth -> 401 for %s", path).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void validAccessTokenReachesAllTwelveServicesInOneProcess() {
        String cust = token("ACCESS", List.of("CUSTOMER"));
        String admin = token("ACCESS", List.of("SUPER_ADMIN"));
        assertThat(get("/api/v1/farm", cust).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/productions", cust).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/inventory/products", cust).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/subscriptions", cust).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/orders", cust).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/cart", cust).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/payments", cust).getStatusCode()).isEqualTo(HttpStatus.OK);   // own-scoped list (read-only)
        assertThat(get("/api/v1/delivery/assignments", admin).getStatusCode()).isEqualTo(HttpStatus.OK); // admin sees all
        assertThat(get("/api/v1/delivery/routes", cust).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/notifications/me", cust).getStatusCode()).isEqualTo(HttpStatus.OK); // any authenticated caller
        assertThat(get("/api/v1/invoices/me", cust).getStatusCode()).isEqualTo(HttpStatus.OK);      // own-scoped list
        assertThat(get("/api/v1/invoices", admin).getStatusCode()).isEqualTo(HttpStatus.OK);        // FARM_MANAGER/SUPER_ADMIN
        assertThat(get("/api/v1/dashboard/summary", admin).getStatusCode()).isEqualTo(HttpStatus.OK); // BFF, SUPER_ADMIN
        assertThat(get("/api/v1/reports/sales", admin).getStatusCode()).isEqualTo(HttpStatus.OK);     // BFF proxy
    }

    // ---- Group 6: dashboard + reports BFF --------------------------------------------

    @Test
    void dashboardRoleRestrictionsPreserved() {
        // GET /dashboard/summary -> hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)
        assertThat(get("/api/v1/dashboard/summary", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        for (String role : List.of("SUPER_ADMIN", "FARM_MANAGER", "DELIVERY_MANAGER")) {
            assertThat(get("/api/v1/dashboard/summary", token("ACCESS", List.of(role))).getStatusCode())
                    .as("dashboard summary as %s", role).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void reportsRoleRestrictionsPreserved() {
        // class-level @PreAuthorize hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)
        for (String path : List.of("/api/v1/reports/sales", "/api/v1/reports/customers", "/api/v1/reports/payments")) {
            assertThat(get(path, token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                    .as("%s as CUSTOMER -> 403", path).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get(path, token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                    .as("%s as DELIVERY_MANAGER -> 200", path).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void dashboardSummaryFansOutToEmbeddedServicesViaInProcessLoopback() {
        // The 8-way parallel Mono.zip hits customer/subscription/order/delivery/payment/inventory/
        // production/notification /summary endpoints as in-process loopback calls (bearer forwarded
        // by RequestHeaderForwarder). A 200 with the aggregated shape proves every hop resolved.
        ResponseEntity<String> resp = get("/api/v1/dashboard/summary", token("ACCESS", List.of("SUPER_ADMIN")));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody())
                .contains("\"totalCustomers\"")
                .contains("\"pendingOrders\"")
                .contains("\"revenueThisMonth\"")
                .contains("\"recentNotifications\"");
    }

    @Test
    void reportExportStreamsValidCsvExcelAndPdfViaLoopback() throws Exception {
        String admin = token("ACCESS", List.of("FARM_MANAGER"));
        int port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
        record Case(String fmt, String contentType, byte[] magic) { }
        for (Case c : List.of(
                new Case("CSV", "text/csv", "Order".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                new Case("EXCEL", "application/vnd.openxmlformats", new byte[]{0x50, 0x4b, 0x03, 0x04}), // xlsx = ZIP
                new Case("PDF", "application/pdf", "%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))) {
            // Raw HttpURLConnection — no HttpMessageConverter negotiation for the vendor xlsx type.
            var url = java.net.URI.create("http://localhost:" + port
                    + "/api/v1/reports/sales/export?format=" + c.fmt()).toURL();
            var conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + admin);
            assertThat(conn.getResponseCode()).as("export %s status", c.fmt()).isEqualTo(200);
            assertThat(conn.getContentType()).as("export %s content-type", c.fmt()).startsWith(c.contentType());
            byte[] body;
            try (var in = conn.getInputStream()) {
                body = in.readAllBytes();
            }
            assertThat(body.length).as("export %s size", c.fmt()).isGreaterThan(c.magic().length);
            assertThat(java.util.Arrays.copyOf(body, c.magic().length)).as("export %s magic", c.fmt())
                    .isEqualTo(c.magic());
        }
    }

    @Test
    void serviceUserPrincipalArgumentResolverAdaptsPerServiceType() {
        String customer = token("ACCESS", List.of("CUSTOMER"));
        // customer.config.UserPrincipal — reads principal.userId()
        assertThat(get("/api/v1/customers/me/consents", customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        // inventory.config.UserPrincipal — CUSTOMER-only
        assertThat(get("/api/v1/reviews/my", customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/reviews/my", token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // subscription.config.UserPrincipal — ownership filter via principal.isAdmin()/userId()
        assertThat(get("/api/v1/subscriptions", customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        // order.config.UserPrincipal — CartController + OrderController inject it everywhere
        assertThat(get("/api/v1/cart", customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/orders", customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        // payment.config.UserPrincipal — PaymentController list is own-scoped via principal
        assertThat(get("/api/v1/payments", customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        // delivery.config.UserPrincipal — GET /delivery/assignments resolves the principal's
        // userId to a DeliveryPartner; a synthetic CUSTOMER has none -> 404 by the endpoint's
        // own design (proves the resolver produced a usable principal, not a 500 NPE).
        assertThat(get("/api/v1/delivery/assignments", customer).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/delivery/assignments", token("ACCESS", List.of("SUPER_ADMIN"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void productionRoleRestrictionsPreserved() {
        assertThat(get("/api/v1/productions/summary/today", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/productions/summary/today", token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void farmRoleRestrictionsPreserved() {
        // hasAnyRole(FARM_MANAGER, SUPER_ADMIN) — valid body so @Valid passes and @PreAuthorize is reached
        assertThat(postJson("/api/v1/farm", "{\"farmName\":\"x\",\"ownerName\":\"y\"}",
                token("ACCESS", List.of("CUSTOMER"))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customerRoleRestrictionsPreserved() {
        // GET /customers -> hasAnyAuthority(SUPER_ADMIN, DELIVERY_MANAGER)
        assertThat(get("/api/v1/customers", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/customers", token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/customers", token("ACCESS", List.of("SUPER_ADMIN"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/customers", token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // POST /api/v1/data-rights-requests/submit is public (no token) -> reaches handler (not 401)
        assertThat(postJson("/api/v1/data-rights-requests/submit",
                "{\"requestType\":\"ACCESS\",\"email\":\"t@example.com\",\"description\":\"x\"}", null)
                .getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void inventoryRoleRestrictionsPreserved() {
        // GET /inventory/summary -> hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)
        assertThat(get("/api/v1/inventory/summary", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/inventory/summary", token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // POST /inventory/product-categories -> hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)
        assertThat(postJson("/api/v1/inventory/product-categories", "{\"name\":\"x\"}",
                token("ACCESS", List.of("CUSTOMER"))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void subscriptionRoleRestrictionsPreserved() {
        // GET /subscriptions/summary (no params) -> hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)
        assertThat(get("/api/v1/subscriptions/summary", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/subscriptions/summary", token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/subscriptions/summary", token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // /analytics/subscription-trend also @PreAuthorize-guarded — a CUSTOMER is 403 even with the
        // required granularity param present (proves method security beats the missing-param 400).
        assertThat(get("/api/v1/subscriptions/analytics/subscription-trend?granularity=DAILY",
                token("ACCESS", List.of("CUSTOMER"))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // plain list/CRUD has no @PreAuthorize — any authenticated caller, ownership-filtered
        assertThat(get("/api/v1/subscriptions", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void orderRoleRestrictionsPreserved() {
        // POST /orders/generate -> hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)
        assertThat(postJson("/api/v1/orders/generate", "{}", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postJson("/api/v1/orders/generate", "{}", token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode())
                .isNotEqualTo(HttpStatus.FORBIDDEN);
        // GET /orders/summary, /orders/reports -> hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)
        assertThat(get("/api/v1/orders/summary", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/orders/summary", token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // GET /orders, /cart — any authenticated caller, ownership-filtered
        assertThat(get("/api/v1/orders", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void paymentRoleRestrictionsPreserved() {
        // /summary, /reports, /export, /analytics/* -> hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)
        assertThat(get("/api/v1/payments/summary", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/payments/summary", token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/payments/reports", token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // POST /payments/callback -> hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER); also disabled -> 404
        assertThat(postJson("/api/v1/payments/callback", "{\"paymentReference\":\"x\",\"success\":true}",
                token("ACCESS", List.of("CUSTOMER"))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postJson("/api/v1/payments/callback", "{\"paymentReference\":\"x\",\"success\":true}",
                token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // POST /payments/{id}/refund -> hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN)
        assertThat(postJson("/api/v1/payments/" + java.util.UUID.randomUUID() + "/refund", "{}",
                token("ACCESS", List.of("CUSTOMER"))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // POST /payments/webhook is PUBLIC (HMAC-verified in the handler) — reaches the handler (not 401)
        assertThat(postJson("/api/v1/payments/webhook", "{}", null).getStatusCode())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deliveryManagerPaymentDetailsGapIsPreservedNotWidened() {
        String sometimePaymentId = jdbc.query(
                "select id from payment.payments where is_deleted = false limit 1",
                rs -> rs.next() ? rs.getString(1) : null);
        assumeThat(sometimePaymentId).as("needs a payment row").isNotNull();
        // Documented pre-existing behaviour: DELIVERY_MANAGER sees reports/list (200)…
        assertThat(get("/api/v1/payments/reports", token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // …but PaymentServiceImpl.isAdmin() is FARM_MANAGER/SUPER_ADMIN only, so an individual
        // non-owned payment's details are NOT visible to DELIVERY_MANAGER. The aggregate must
        // NOT widen this just because payment is now in-process.
        assertThat(get("/api/v1/payments/" + sometimePaymentId, token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/payments/" + sometimePaymentId, token("ACCESS", List.of("SUPER_ADMIN"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void deliveryRoleRestrictionsPreserved() {
        // /summary, /reports, /analytics/performance-trend -> hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)
        assertThat(get("/api/v1/delivery/assignments/summary", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/delivery/assignments/summary", token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // POST /delivery/partners, /delivery/routes -> hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN) — NOT DELIVERY_MANAGER
        for (String role : List.of("CUSTOMER", "DELIVERY_MANAGER")) {
            assertThat(postJson("/api/v1/delivery/partners",
                    "{\"userId\":\"" + java.util.UUID.randomUUID() + "\",\"name\":\"x\",\"mobile\":\"9990000000\"}",
                    token("ACCESS", List.of(role))).getStatusCode())
                    .as("POST /delivery/partners as %s -> 403", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
        // GET /delivery/routes — any authenticated caller
        assertThat(get("/api/v1/delivery/routes", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void paymentToOrderResolvesAsInProcessLoopback() {
        // POST /api/v1/payments validates the order via lb://order-service before touching the
        // wallet. Use a real order + its own customer so the order lookup succeeds; a WALLET
        // payment then fails cleanly on ₹0 balance (409) — that 409 (not a 5xx / "order not
        // found") proves the in-process loopback + Authorization forwarding worked.
        var row = jdbc.query("select id, customer_id, total_amount from \"order\".orders "
                + "where is_deleted = false and total_amount > 0 limit 1", rs ->
                rs.next() ? new String[]{rs.getString(1), rs.getString(2), rs.getString(3)} : null);
        assumeThat(row).as("needs an order row").isNotNull();
        String body = "{\"orderId\":\"" + row[0] + "\",\"amount\":" + row[2] + ",\"paymentMethod\":\"WALLET\"}";
        // token whose userId == the order's customer (ownership check on payment initiation)
        String custToken = tokenFor(row[1], "CUSTOMER");
        ResponseEntity<String> resp = postJson("/api/v1/payments", body, custToken);
        try {
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resp.getBody()).contains("wallet balance");
        } finally {
            jdbc.update("delete from payment.wallet_transactions where wallet_id in "
                    + "(select id from payment.wallets where customer_id = ?::uuid)", row[1]);
            jdbc.update("delete from payment.wallets where customer_id = ?::uuid and balance = 0", row[1]);
        }
    }

    @Test
    void customerToFarmCallResolvesAsInProcessLoopback() {
        String someCustomerId = jdbc.query(
                "select id from customer.customers where is_deleted = false limit 1",
                rs -> rs.next() ? rs.getString(1) : null);
        assumeThat(someCustomerId).as("needs at least one customer row").isNotNull();
        // This endpoint calls lb://farm-service/api/v1/farm/business-settings. In the aggregate
        // that is rewritten to an in-process loopback; a 200 (not 422 "could not verify") proves
        // the loopback + Authorization forwarding worked.
        ResponseEntity<String> resp = get("/api/v1/customers/" + someCustomerId + "/delivery-availability",
                token("ACCESS", List.of("SUPER_ADMIN")));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("deliveryRadiusKm");
    }

    @Test
    void notificationRoleRestrictionsPreserved() {
        UUID recipient = UUID.randomUUID();
        // GET /notifications/logs -> hasAnyAuthority(FARM_MANAGER, SUPER_ADMIN); recipientId is required
        assertThat(get("/api/v1/notifications/logs?recipientId=" + recipient, token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/notifications/logs?recipientId=" + recipient, token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // GET /notifications/summary -> hasAnyAuthority(SUPER_ADMIN, FARM_MANAGER, DELIVERY_MANAGER)
        assertThat(get("/api/v1/notifications/summary", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/notifications/summary", token("ACCESS", List.of("DELIVERY_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // GET /notifications/me -> any authenticated caller; principal.userId() resolved from the token
        assertThat(get("/api/v1/notifications/me", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void invoiceRoleRestrictionsPreserved() {
        // GET /invoices (list) -> hasAnyRole(FARM_MANAGER, SUPER_ADMIN)
        assertThat(get("/api/v1/invoices", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/invoices", token("ACCESS", List.of("FARM_MANAGER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // POST /invoices/generate/{orderId} -> hasAnyRole(FARM_MANAGER, SUPER_ADMIN); CUSTOMER blocked
        // by method security before any downstream loopback call.
        assertThat(postJson("/api/v1/invoices/generate/" + UUID.randomUUID(), "",
                token("ACCESS", List.of("CUSTOMER"))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // GET /invoices/me -> any authenticated caller, own-scoped by the stored customerId
        assertThat(get("/api/v1/invoices/me", token("ACCESS", List.of("CUSTOMER"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void invoiceGenerationDrivesInProcessLoopbackToOrderPaymentCustomerAndRendersPdf() {
        // Pick a real order with no existing invoice AND whose customer row is resolvable — the
        // test asserts the customer-service loopback returned real data (customerName), so the
        // chosen order must reference a live customer.customers row (some seed orders point at
        // customers that no longer exist). ORDER BY for a deterministic pick. invoice-service
        // composes the invoice from order + payment + customer data via lb:// calls that, in the
        // aggregate, are in-process loopback hops authenticated by the per-process X-Internal-Auth
        // secret (invoice's clients send a fixed SUPER_ADMIN X-User-* identity, not a bearer).
        String orderId = jdbc.query(
                "select o.id from \"order\".orders o "
                        + "join customer.customers c on c.id = o.customer_id and c.is_deleted = false "
                        + "where o.is_deleted = false and o.total_amount > 0 "
                        + "and not exists (select 1 from invoice.invoices i where i.order_id = o.id and i.is_deleted = false) "
                        + "order by o.id limit 1",
                rs -> rs.next() ? rs.getString(1) : null);
        assumeThat(orderId).as("needs an order with no invoice and a live customer").isNotNull();

        String admin = token("ACCESS", List.of("FARM_MANAGER"));
        ResponseEntity<String> gen = postJson("/api/v1/invoices/generate/" + orderId, "", admin);
        try {
            // 201 (not 5xx / "order not found") proves order+payment+customer loopbacks all authenticated.
            assertThat(gen.getStatusCode()).as("generate: %s", gen.getBody()).isEqualTo(HttpStatus.CREATED);
            assertThat(gen.getBody()).contains("\"invoiceNumber\":\"INV-");
            // customerName present -> the customer-service loopback returned real data (not a stub)
            assertThat(gen.getBody()).contains("\"customerName\":\"");

            String invoiceId = jdbc.queryForObject(
                    "select id from invoice.invoices where order_id = ?::uuid and is_deleted = false", String.class, orderId);

            // PDF: real bytes, application/pdf, %PDF header + %%EOF trailer (PDFBox in-memory render).
            ResponseEntity<byte[]> pdf = rest.exchange("/api/v1/invoices/" + invoiceId + "/pdf",
                    HttpMethod.GET, new HttpEntity<>(bearer(admin)), byte[].class);
            assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(pdf.getHeaders().getContentType().toString()).startsWith("application/pdf");
            byte[] body = pdf.getBody();
            assertThat(body).isNotNull();
            assertThat(new String(body, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
            assertThat(body.length).isGreaterThan(500);
        } finally {
            jdbc.update("delete from invoice.invoices where order_id = ?::uuid", orderId);
            jdbc.update("delete from farm.audit_log where service_name = 'farm2home-spike'");
        }
    }

    @Test
    void kafkaListenerContainersDoNotAutoStart() {
        // covers customer's CustomerEventConsumer AND order's 2 consumers (Delivery/Subscription).
        // notification-service's NotificationEventConsumer / its `kafkaListenerContainerFactory` is
        // deliberately NOT scanned (see Application.java) so it never registers a container here.
        var registry = ctx.getBean(org.springframework.kafka.config.KafkaListenerEndpointRegistry.class);
        assertThat(registry.getListenerContainers())
                .allSatisfy(c -> assertThat(c.isRunning()).as("container %s must be stopped", c.getListenerId()).isFalse());
        assertThat(ctx.getBeanNamesForType(com.farm2home.notification.kafka.NotificationEventConsumer.class))
                .as("notification.kafka is not scanned").isEmpty();
    }

    @Test
    void orderCreationDrivesInProcessLoopbackToCustomerFarmAndInventory() {
        String someCustomerId = jdbc.query(
                "select id from customer.customers where is_deleted = false limit 1",
                rs -> rs.next() ? rs.getString(1) : null);
        assumeThat(someCustomerId).as("needs a customer row").isNotNull();

        // A milk-item manual order: order-service calls lb://customer-service/.../delivery-availability
        // (which itself calls lb://farm-service/.../business-settings) — a 2-hop in-process loopback.
        String body = "{\"customerId\":\"" + someCustomerId + "\",\"orderDate\":\"2099-12-31\","
                + "\"items\":[{\"milkType\":\"TONED\",\"quantity\":2}]}";
        ResponseEntity<String> resp = postJson("/api/v1/orders", body, token("ACCESS", List.of("SUPER_ADMIN")));
        try {
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody()).contains("\"orderNumber\"").contains("\"totalAmount\":130");  // 2L TONED @ 65
        } finally {
            jdbc.update("delete from \"order\".order_items where order_id in "
                    + "(select id from \"order\".orders where order_date = date '2099-12-31')");
            jdbc.update("delete from \"order\".orders where order_date = date '2099-12-31'");
            jdbc.update("delete from farm.audit_log where service_name = 'farm2home-spike'");
        }
    }

    // ---- helpers ----------------------------------------------------------------------

    /** Quotes the schema so a reserved word like {@code order} is valid SQL. */
    private static String q(String schema) {
        return "\"" + schema + "\"";
    }

    private int appliedMigrations(String schema) {
        Integer n = jdbc.queryForObject(
                "select count(*) from " + q(schema) + ".flyway_schema_history where success and version is not null",
                Integer.class);
        return n == null ? 0 : n;
    }

    private List<String> descriptions(String schema) {
        return jdbc.queryForList(
                "select description from " + q(schema) + ".flyway_schema_history where version is not null", String.class);
    }

    private boolean tableExists(String schema, String table) {
        Integer n = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = ? and table_name = ?",
                Integer.class, schema, table);
        return n != null && n > 0;
    }

    private ResponseEntity<String> get(String path, String bearer) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(bearer)), String.class);
    }

    private ResponseEntity<String> postJson(String path, String body, String bearer) {
        HttpHeaders h = bearer(bearer);
        h.add("Content-Type", "application/json");
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return h;
    }

    private String token(String type, List<String> roles) {
        return jwt(type, "11111111-1111-1111-1111-111111111111", roles);
    }

    /** ACCESS token whose {@code userId} claim is a specific customer (for ownership-checked flows). */
    private String tokenFor(String userId, String role) {
        return jwt("ACCESS", userId, List.of(role));
    }

    private String jwt(String type, String userId, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("9876500000")
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000))
                .signWith(key)
                .compact();
    }
}
