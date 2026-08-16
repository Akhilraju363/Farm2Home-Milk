package com.farm2home.customer.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @LoadBalanced resolves "lb://farm-service" against Eureka the same way the gateway's "lb://"
 * routes do. Needed here so delivery-availability checks can fetch Farm2Home's business
 * settings (origin lat/lng + radius) without hardcoding farm-service's host:port.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
