package com.farm2home.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated executor for {@code PaymentEventListener}'s {@code @Async} Kafka publish, kept
 * separate from {@code common-core}'s {@code auditTaskExecutor} so a burst of payment events
 * can never starve audit-log writes (or vice versa). {@code @EnableAsync} itself is already
 * active platform-wide via {@code AuditAutoConfiguration} — no need to redeclare it here.
 */
@Configuration
public class PaymentAsyncConfig {

    @Bean(name = "paymentEventExecutor")
    public Executor paymentEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("payment-event-");
        executor.initialize();
        return executor;
    }
}
