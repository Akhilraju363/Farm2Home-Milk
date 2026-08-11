package com.farm2home.invoice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @LoadBalanced resolves "lb://order-service" etc. against Eureka the same way the gateway's
 * "lb://" routes do. invoice-service composes its response from order-service, payment-service,
 * and customer-service at read time rather than duplicating their data (see each Client class).
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
