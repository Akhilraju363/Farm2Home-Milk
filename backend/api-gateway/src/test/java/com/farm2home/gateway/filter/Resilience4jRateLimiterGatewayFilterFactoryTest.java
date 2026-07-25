package com.farm2home.gateway.filter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Resilience4jRateLimiterGatewayFilterFactoryTest {

    @Test
    @DisplayName("permit available → request passes through to the chain")
    void permitAvailable_passesThrough() {
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();
        var factory = new Resilience4jRateLimiterGatewayFilterFactory(registry);

        var config = new Resilience4jRateLimiterGatewayFilterFactory.Config();
        config.setName("testRL");
        GatewayFilter filter = factory.apply(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull(); // untouched by the filter
    }

    @Test
    @DisplayName("permit already exhausted → rejects with 429 without invoking the chain")
    void noPermits_returns429() {
        RateLimiterRegistry registry = RateLimiterRegistry.of(
                RateLimiterConfig.custom().limitForPeriod(1).limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO).build());
        // Same name -> same cached RateLimiter instance the filter itself will look up.
        RateLimiter rateLimiter = registry.rateLimiter("exhaustedRL");
        assertThat(rateLimiter.acquirePermission()).isTrue(); // consumes the only permit for this period

        var factory = new Resilience4jRateLimiterGatewayFilterFactory(registry);
        var config = new Resilience4jRateLimiterGatewayFilterFactory.Config();
        config.setName("exhaustedRL");
        GatewayFilter filter = factory.apply(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Config getter/setter round-trip")
    void configGetterSetter() {
        var config = new Resilience4jRateLimiterGatewayFilterFactory.Config();
        config.setName("myRL");
        assertThat(config.getName()).isEqualTo("myRL");
    }
}
