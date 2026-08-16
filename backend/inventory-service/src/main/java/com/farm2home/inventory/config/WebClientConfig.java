package com.farm2home.inventory.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** @LoadBalanced resolves "lb://order-service" etc. against Eureka the same way the gateway's
 *  "lb://" routes do - see invoice-service's WebClientConfig, the pattern this mirrors. */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
