package com.farm2home.gateway.filter;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Caps concurrent in-flight calls to a downstream route using a Resilience4j Bulkhead.
 * Spring Cloud Gateway has no built-in bulkhead filter, so this wraps the reactor
 * BulkheadOperator directly around the filter chain. Registered as bean name
 * "Resilience4jBulkhead" (the "GatewayFilterFactory" suffix is stripped for the YAML
 * filter `name`).
 */
@Component
public class Resilience4jBulkheadGatewayFilterFactory
        extends AbstractGatewayFilterFactory<Resilience4jBulkheadGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jBulkheadGatewayFilterFactory.class);

    private final BulkheadRegistry bulkheadRegistry;

    public Resilience4jBulkheadGatewayFilterFactory(BulkheadRegistry bulkheadRegistry) {
        super(Config.class);
        this.bulkheadRegistry = bulkheadRegistry;
    }

    @Override
    public GatewayFilter apply(Config config) {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(config.getName());
        return (exchange, chain) -> chain.filter(exchange)
                .transformDeferred(BulkheadOperator.of(bulkhead))
                .onErrorResume(BulkheadFullException.class, ex -> {
                    log.warn("Bulkhead full for '{}' on {}", config.getName(), exchange.getRequest().getPath());
                    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
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
