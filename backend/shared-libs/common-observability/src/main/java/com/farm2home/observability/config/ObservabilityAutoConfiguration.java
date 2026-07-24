package com.farm2home.observability.config;

import com.farm2home.observability.web.ReactiveRequestTraceIdFilter;
import com.farm2home.observability.web.RequestTraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
}
