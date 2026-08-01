package com.farm2home.payment.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @LoadBalanced resolves "lb://order-service" against Eureka the same way the gateway's "lb://"
 * routes do, so client classes never hardcode a host:port - only the service's registered
 * application name. Needed here (unlike most business services) because payment initiation
 * validates the target order's status against order-service before accepting a payment.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
