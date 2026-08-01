package com.farm2home.delivery.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @LoadBalanced resolves "lb://payment-service" against Eureka the same way the gateway's
 * "lb://" routes do, so client classes never hardcode a host:port - only the service's
 * registered application name. Needed here because dispatching a delivery validates payment
 * progress against payment-service first.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
