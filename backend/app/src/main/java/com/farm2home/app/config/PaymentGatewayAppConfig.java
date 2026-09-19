package com.farm2home.app.config;

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
 * App-side replacement for payment-service's excluded {@code config.PaymentGatewayConfig}.
 *
 * <p>{@code PaymentServiceImpl} requires a {@link PaymentGatewayProvider}; the concrete
 * providers ({@code MockPaymentGatewayProvider} / {@code RazorpayPaymentGatewayProvider}) are
 * plain classes {@code @Bean}-wired by the original config, not {@code @Component}s, so
 * scanning {@code payment.gateway} would find nothing — this config supplies them instead,
 * keeping the exact same selection semantics:
 *
 * <ul>
 *   <li>{@code payment.gateway.provider=mock} (default via {@code matchIfMissing}) —
 *       {@code MockPaymentGatewayProvider}: <b>no external calls, no credentials</b>. This is
 *       what every test and the default local/Render deployment use.</li>
 *   <li>{@code payment.gateway.provider=razorpay} — only when {@code RAZORPAY_KEY_ID/SECRET}
 *       are supplied via the environment (never in source). Uses a plain (non-load-balanced)
 *       {@code WebClient.Builder} — Razorpay is an external HTTPS API, not an {@code lb://}
 *       service.</li>
 * </ul>
 *
 * <p>{@code @EnableConfigurationProperties(RazorpayProperties.class)} — that class lives in the
 * never-scanned {@code payment.gateway.razorpay} package. Its {@code key-secret}/
 * {@code webhook-secret} fields default to {@code ""} and are Actuator-sanitised.
 */
@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class PaymentGatewayAppConfig {

    @Bean
    @ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "razorpay")
    PaymentGatewayProvider razorpayPaymentGatewayProvider(RazorpayProperties properties, ObjectMapper objectMapper) {
        return new RazorpayPaymentGatewayProvider(WebClient.builder(), properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "mock", matchIfMissing = true)
    PaymentGatewayProvider mockPaymentGatewayProvider() {
        return new MockPaymentGatewayProvider();
    }
}
