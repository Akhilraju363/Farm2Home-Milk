package com.farm2home.app.config;

import com.farm2home.order.config.MilkPriceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers order-service's {@link MilkPriceProperties} ({@code @ConfigurationProperties(prefix
 * = "milk")}).
 *
 * <p>It lives in {@code com.farm2home.order.config}, which this app never component-scans, so
 * its {@code @Component} stereotype is inert here — but {@code OrderServiceImpl} and
 * {@code DailyOrderGenerationService} both require it. {@code @EnableConfigurationProperties}
 * registers exactly one instance, bound from the {@code milk.prices.*} block copied into this
 * module's {@code application.yml} (verbatim from order-service's own yml).
 */
@Configuration
@EnableConfigurationProperties(MilkPriceProperties.class)
public class OrderPricingConfig {
}
