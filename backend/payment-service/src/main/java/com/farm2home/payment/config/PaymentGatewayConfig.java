package com.farm2home.payment.config;

import com.farm2home.payment.gateway.PaymentGatewayProvider;
import com.farm2home.payment.gateway.mock.MockPaymentGatewayProvider;
import com.farm2home.payment.gateway.razorpay.RazorpayPaymentGatewayProvider;
import com.farm2home.payment.gateway.razorpay.RazorpayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Selects the active {@link PaymentGatewayProvider} from {@code payment.gateway.provider}
 * (environment variable {@code PAYMENT_GATEWAY_PROVIDER}) — {@code mock} by default so local
 * development and every test run without real gateway credentials; set to {@code razorpay} in
 * any environment that has {@code RAZORPAY_KEY_ID}/{@code RAZORPAY_KEY_SECRET} configured.
 *
 * Deliberately builds its own plain {@code WebClient.Builder} rather than injecting the
 * {@code @LoadBalanced} one from {@code WebClientConfig} — Razorpay is an external HTTPS API, not
 * a Eureka-registered service, so load-balancer-aware URI resolution ("lb://...") doesn't apply.
 */
@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class PaymentGatewayConfig {

    @Bean
    @ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "razorpay")
    public PaymentGatewayProvider razorpayPaymentGatewayProvider(RazorpayProperties properties,
            ObjectMapper objectMapper) {
        return new RazorpayPaymentGatewayProvider(WebClient.builder(), properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "mock", matchIfMissing = true)
    public PaymentGatewayProvider mockPaymentGatewayProvider() {
        return new MockPaymentGatewayProvider();
    }
}
