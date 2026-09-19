package com.farm2home.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Single-JVM aggregation entry point (branch {@code spike/render-single-jvm}, P1-1).
 *
 * <p>Aggregates business components from a controlled, growing set of services into ONE servlet
 * Spring Boot context — no Eureka, no Config Server, no Spring Cloud Gateway, no Kafka broker.
 *
 * <pre>
 *   Group 1 (verified): farm, production
 *   Group 2 (verified): customer, inventory
 *   Group 3 (verified): subscription, order
 *   Group 4 (verified): payment, delivery
 *   Group 5 (verified): notification, invoice
 *   Group 6 (this change): dashboard, reports
 * </pre>
 *
 * <h2>Component-scan strategy (unchanged principle, extended list)</h2>
 * Never {@code scanBasePackages = "com.farm2home"}. For each embedded service, scan ONLY the
 * leaf packages that hold business beans:
 * <ul>
 *   <li>{@code controller} — REST controllers</li>
 *   <li>{@code service} (incl. {@code service.impl}) — business services</li>
 *   <li>{@code mapper} — MapStruct {@code *Impl} ({@code componentModel = "spring"})</li>
 *   <li>{@code client} — {@code @Component} outbound REST clients (need the app-level
 *       {@code loadBalancedWebClientBuilder} from {@code LoadBalancerClientConfig}, which
 *       rewrites their {@code lb://<service>} URIs to an in-process loopback)</li>
 *   <li>{@code kafka} — {@code @Component} producers/consumers AND {@code KafkaConfig}
 *       (its {@code KafkaTemplate} / listener-container-factory beans are needed by those
 *       components; the broker stays unreachable — see {@code application.yml}
 *       {@code spring.kafka.listener.auto-startup=false})</li>
 * </ul>
 * The {@code config} package of every service is <b>never</b> scanned — each declares a
 * {@code SecurityConfig} ({@code SecurityFilterChain}), {@code JpaConfig}
 * ({@code @EnableJpaRepositories} — does not compose), {@code @Component("auditorAwareImpl")},
 * {@code @Component GatewayHeaderAuthFilter}, {@code WebClientConfig}
 * ({@code loadBalancedWebClientBuilder}) and {@code OpenApiConfig}. This module supplies exactly
 * one of each (see {@code com.farm2home.app.config} / {@code com.farm2home.app.security}).
 *
 * <h2>{@code FullyQualifiedAnnotationBeanNameGenerator}</h2>
 * Multiple services ship a same-simple-named {@code @Configuration} class in a scanned package —
 * every {@code com.farm2home.<svc>.kafka.KafkaConfig} would otherwise register under the bean
 * name {@code kafkaConfig} and collide. The FQN generator names every scanned component by its
 * fully-qualified class name, so such classes coexist. Verified safe for this codebase: nothing
 * looks a scanned bean up by simple name ({@code @Qualifier("x")} / {@code getBean("x")} /
 * {@code @Resource(name=)} / {@code @DependsOn} — none exist); all wiring is by-type
 * ({@code @RequiredArgsConstructor} + {@code private final X}). {@code @Bean}-method names
 * (e.g. {@code customerKafkaListenerContainerFactory}) are unaffected by this generator, and the
 * one explicitly-named bean this app relies on — {@code auditorAwareImpl} — is declared with an
 * explicit {@code @Bean(name = ...)}.
 */
@SpringBootApplication(
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        scanBasePackages = {
                "com.farm2home.app",

                // Group 1
                "com.farm2home.farm.controller",
                "com.farm2home.farm.service",
                "com.farm2home.farm.mapper",
                "com.farm2home.production.controller",
                "com.farm2home.production.service",
                "com.farm2home.production.mapper",

                // Group 2
                "com.farm2home.customer.controller",
                "com.farm2home.customer.service",
                "com.farm2home.customer.mapper",
                "com.farm2home.customer.client",
                "com.farm2home.customer.kafka",
                "com.farm2home.inventory.controller",
                "com.farm2home.inventory.service",
                "com.farm2home.inventory.mapper",
                "com.farm2home.inventory.client",
                "com.farm2home.inventory.kafka",

                // Group 3
                "com.farm2home.subscription.controller",
                "com.farm2home.subscription.service",
                "com.farm2home.subscription.mapper",
                "com.farm2home.subscription.kafka",       // subscription has no client package
                "com.farm2home.order.controller",
                "com.farm2home.order.service",
                "com.farm2home.order.mapper",
                "com.farm2home.order.client",
                "com.farm2home.order.kafka",

                // Group 4
                "com.farm2home.payment.controller",
                "com.farm2home.payment.service",
                "com.farm2home.payment.mapper",
                "com.farm2home.payment.client",
                "com.farm2home.payment.kafka",
                "com.farm2home.payment.scheduler",         // PaymentReconciliationJob (@Scheduled, mock-safe)
                // NOT com.farm2home.payment.event — PaymentEventListener only does a (dead) Kafka
                //   publish via @Async("paymentEventExecutor"); PaymentServiceImpl publishes through
                //   ApplicationEventPublisher, so skipping it needs no PaymentAsyncConfig here.
                // NOT com.farm2home.payment.gateway — providers are plain classes @Bean-wired by
                //   PaymentGatewayConfig; the app supplies them via PaymentGatewayAppConfig.
                "com.farm2home.delivery.controller",
                "com.farm2home.delivery.service",
                "com.farm2home.delivery.mapper",
                "com.farm2home.delivery.client",
                "com.farm2home.delivery.kafka",

                // Group 5
                "com.farm2home.notification.controller",
                "com.farm2home.notification.service",
                "com.farm2home.notification.mapper",
                "com.farm2home.notification.client",
                // NOT com.farm2home.notification.kafka — its KafkaConfig declares a @Bean method
                //   literally named `kafkaListenerContainerFactory`, identical to order-service's,
                //   which would collide (the FQN generator renames scanned *classes*, not @Bean
                //   *methods*). notification is Kafka-CONSUMER-ONLY; with no broker the consumer +
                //   its factory are inert, and NotificationServiceImpl (REST) does not depend on
                //   them. So the whole package is left unscanned — see GROUP5_REPORT.md §10.
                "com.farm2home.invoice.controller",
                "com.farm2home.invoice.service",
                "com.farm2home.invoice.mapper",           // invoice has no mapper package
                "com.farm2home.invoice.client",

                // Group 6 — dashboard + reports are pure BFF aggregators: NO domain/entity/
                //   repository package, NO kafka package, NO mapper package, NO scheduler.
                //   Their config packages (SecurityConfig, WebClientConfig with the same
                //   `loadBalancedWebClientBuilder` bean name, GatewayHeaderAuthFilter,
                //   UserPrincipal, OpenApiConfig) are NOT scanned — the app supplies one of each.
                "com.farm2home.dashboard.controller",
                "com.farm2home.dashboard.service",
                "com.farm2home.dashboard.client",
                "com.farm2home.reports.controller",
                "com.farm2home.reports.service",
                "com.farm2home.reports.client"
        }
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
