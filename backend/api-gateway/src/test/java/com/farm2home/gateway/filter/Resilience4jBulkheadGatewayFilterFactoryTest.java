package com.farm2home.gateway.filter;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
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

class Resilience4jBulkheadGatewayFilterFactoryTest {

    @Test
    @DisplayName("slot available → request passes through to the chain")
    void slotAvailable_passesThrough() {
        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
        var factory = new Resilience4jBulkheadGatewayFilterFactory(registry);

        var config = new Resilience4jBulkheadGatewayFilterFactory.Config();
        config.setName("testBH");
        GatewayFilter filter = factory.apply(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull(); // untouched by the filter
    }

    @Test
    @DisplayName("zero concurrent calls allowed → rejects with 503 without invoking the chain")
    void noCapacity_returns503() {
        BulkheadRegistry registry = BulkheadRegistry.of(
                BulkheadConfig.custom().maxConcurrentCalls(0).maxWaitDuration(Duration.ZERO).build());
        var factory = new Resilience4jBulkheadGatewayFilterFactory(registry);

        var config = new Resilience4jBulkheadGatewayFilterFactory.Config();
        config.setName("fullBH");
        GatewayFilter filter = factory.apply(config);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Config getter/setter round-trip")
    void configGetterSetter() {
        var config = new Resilience4jBulkheadGatewayFilterFactory.Config();
        config.setName("myBH");
        assertThat(config.getName()).isEqualTo("myBH");
    }
}
