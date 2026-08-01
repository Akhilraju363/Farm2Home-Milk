package com.farm2home.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @LoadBalanced resolves "lb://production-service" against Eureka the same way the gateway's
 * "lb://" routes do, so client classes never hardcode a host:port - only the service's
 * registered application name. Needed here because creating a same-day manual order validates
 * it against today's recorded milk production first.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
