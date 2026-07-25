package com.farm2home.gateway.filter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Spring Cloud Gateway ships a RequestRateLimiter filter, but its only built-in
 * implementation is Redis-backed. This wraps a Resilience4j (in-memory, per-instance)
 * RateLimiter as a gateway filter instead, so routes don't take on a Redis dependency
 * just to rate-limit. Registered as bean name "Resilience4jRateLimiter" (the
 * "GatewayFilterFactory" suffix is stripped for use as the YAML filter `name`).
 */
@Component
public class Resilience4jRateLimiterGatewayFilterFactory
        extends AbstractGatewayFilterFactory<Resilience4jRateLimiterGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jRateLimiterGatewayFilterFactory.class);

    private final RateLimiterRegistry rateLimiterRegistry;

    public Resilience4jRateLimiterGatewayFilterFactory(RateLimiterRegistry rateLimiterRegistry) {
        super(Config.class);
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    public GatewayFilter apply(Config config) {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(config.getName());
        return (exchange, chain) -> chain.filter(exchange)
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorResume(RequestNotPermitted.class, ex -> {
                    log.warn("Rate limit exceeded for '{}' on {}", config.getName(), exchange.getRequest().getPath());
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                });
    }

    public static class Config {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
