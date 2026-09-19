package com.farm2home.observability.config;

import com.farm2home.observability.health.ConfigServerHealthIndicator;
import com.farm2home.observability.health.ReactiveConfigServerHealthIndicator;
import com.farm2home.observability.web.ReactiveRequestTraceIdFilter;
import com.farm2home.observability.web.RequestTraceIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.WebFilter;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Bean
    public StartupShutdownLogger startupShutdownLogger(Environment environment) {
        return new StartupShutdownLogger(environment);
    }

    // A servlet/reactive split guarded only at @Bean-method level isn't enough: Spring still
    // resolves a conditional method's declared return type (FilterRegistrationBean<RequestTraceIdFilter>,
    // which reaches jakarta.servlet.Filter) while evaluating unrelated conditions elsewhere in the
    // context, even though the method itself would never be invoked. A reactive-only app like the
    // API Gateway has no servlet API on its classpath at all, so that alone threw NoClassDefFoundError
    // and crashed startup. Class-level conditions on a nested @Configuration make Spring skip parsing
    // the class entirely when the condition fails, so its method signatures are never touched.

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    static class ServletFilterConfiguration {

        @Bean
        public FilterRegistrationBean<RequestTraceIdFilter> requestTraceIdFilter() {
            FilterRegistrationBean<RequestTraceIdFilter> registration =
                    new FilterRegistrationBean<>(new RequestTraceIdFilter());
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            registration.addUrlPatterns("/*");
            registration.setName("requestTraceIdFilter");
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebFilter.class)
    static class ReactiveFilterConfiguration {

        @Bean
        public ReactiveRequestTraceIdFilter reactiveRequestTraceIdFilter() {
            return new ReactiveRequestTraceIdFilter();
        }
    }

    // No service actually imports config from Config Server today, so Spring Cloud Config
    // never auto-registers a health check for it - these two mirror the servlet/reactive split
    // above for the same NoClassDefFoundError reason. Defaults assume local dev (localhost:8888
    // with the default local credentials); override configserver.health.url/username/password
    // (e.g. via CONFIGSERVER_HEALTH_URL) for Docker or any other environment.
    //
    // @ConditionalOnMissingClass: config-server itself (the only module with spring-cloud-config-
    // server on its classpath) already registers its own bean named "configServerHealthIndicator"
    // (checking its own environment repository, no guard of its own) - a service checking its own
    // reachability over HTTP would be circular anyway. @ConditionalOnMissingBean was tried first
    // and doesn't work here: it only stops *this* bean from registering if one already exists,
    // but Spring Cloud Config's own definition has no such guard, so whichever one processes
    // second still crashes on the name collision. Excluding this whole nested class by classpath
    // presence sidesteps registration order entirely.

    // Opt-out switch (default on): a deployment that genuinely runs without a Config Server -
    // e.g. the single-JVM render spike - sets farm2home.observability.config-server-health.enabled=false
    // so an unreachable localhost:8888 does not drag /actuator/health to DOWN. Every existing
    // service leaves it unset and is completely unaffected.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    @ConditionalOnMissingClass("org.springframework.cloud.config.server.config.ConfigServerProperties")
    @ConditionalOnProperty(prefix = "farm2home.observability.config-server-health",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ServletConfigServerHealthConfiguration {

        @Bean
        public ConfigServerHealthIndicator configServerHealthIndicator(
                @Value("${configserver.health.url:http://localhost:8888}") String url,
                @Value("${configserver.health.username:${CONFIG_USERNAME:configuser}}") String username,
                @Value("${configserver.health.password:${CONFIG_PASSWORD:config@secret}}") String password) {
            return new ConfigServerHealthIndicator(url, username, password);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebFilter.class)
    @ConditionalOnProperty(prefix = "farm2home.observability.config-server-health",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ReactiveConfigServerHealthConfiguration {

        @Bean
        public ReactiveConfigServerHealthIndicator configServerHealthIndicator(
                @Value("${configserver.health.url:http://localhost:8888}") String url,
                @Value("${configserver.health.username:${CONFIG_USERNAME:configuser}}") String username,
                @Value("${configserver.health.password:${CONFIG_PASSWORD:config@secret}}") String password) {
            return new ReactiveConfigServerHealthIndicator(url, username, password);
        }
    }
}
