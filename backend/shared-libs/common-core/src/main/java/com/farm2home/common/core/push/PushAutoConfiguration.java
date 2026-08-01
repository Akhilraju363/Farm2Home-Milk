package com.farm2home.common.core.push;

import com.farm2home.common.core.audit.AuditLogService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaVendorAdapter;

/**
 * Registers {@link PushProvider}/{@link PushService} beans platform-wide, mirroring
 * {@code SmsAutoConfiguration} (itself mirroring {@code AuditAutoConfiguration}) exactly. Every
 * business service picks this up automatically just by depending on common-core.
 *
 * Provider selection is entirely configuration-driven ({@code push.provider}, default
 * {@code logging}) - a future real provider (Firebase Cloud Messaging, APNs, OneSignal, ...) is
 * added by defining its own {@code @ConditionalOnProperty(name = "push.provider", havingValue =
 * "...")}-gated {@code PushProvider} {@code @Bean}, exactly the shape used for both Razorpay
 * (payment-service) and the SMS abstraction. The default logging provider backs off via
 * {@code @ConditionalOnMissingBean} if any other {@link PushProvider} bean is present.
 */
@AutoConfiguration
@ConditionalOnClass(JpaVendorAdapter.class)
public class PushAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PushProvider.class)
    @ConditionalOnProperty(name = "push.provider", havingValue = "logging", matchIfMissing = true)
    public PushProvider loggingPushProvider() {
        return new LoggingPushProvider();
    }

    @Bean
    public PushProperties pushProperties() {
        return new PushProperties();
    }

    @Bean
    @ConditionalOnMissingBean(PushService.class)
    public PushService pushService(PushProvider pushProvider, AuditLogService auditLogService, PushProperties properties) {
        return new PushService(pushProvider, auditLogService, properties.getMaxAttempts(), properties.getRetryBackoffMillis());
    }
}
