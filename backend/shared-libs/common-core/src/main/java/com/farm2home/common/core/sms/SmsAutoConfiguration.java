package com.farm2home.common.core.sms;

import com.farm2home.common.core.audit.AuditLogService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaVendorAdapter;

/**
 * Registers {@link SmsProvider}/{@link SmsService} beans platform-wide, mirroring
 * {@code AuditAutoConfiguration}'s pattern exactly (same JPA/AuditLogService dependency, same
 * "zero per-service wiring" goal). Every business service picks this up automatically just by
 * depending on common-core.
 *
 * Provider selection is entirely configuration-driven ({@code sms.provider}, default
 * {@code logging}) - a future real provider (Twilio, MSG91, AWS SNS, ...) is added by defining
 * its own {@code @ConditionalOnProperty(name = "sms.provider", havingValue = "...")}-gated
 * {@code SmsProvider} {@code @Bean} (either here or directly in a consuming service), exactly the
 * shape {@code payment-service}'s {@code PaymentGatewayConfig} already uses for Razorpay. The
 * default logging provider backs off via {@code @ConditionalOnMissingBean} if any other
 * {@link SmsProvider} bean is present, so a service can also just define its own without touching
 * the {@code sms.provider} property at all.
 */
@AutoConfiguration
@ConditionalOnClass(JpaVendorAdapter.class)
public class SmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SmsProvider.class)
    @ConditionalOnProperty(name = "sms.provider", havingValue = "logging", matchIfMissing = true)
    public SmsProvider loggingSmsProvider() {
        return new LoggingSmsProvider();
    }

    @Bean
    public SmsProperties smsProperties() {
        return new SmsProperties();
    }

    @Bean
    @ConditionalOnMissingBean(SmsService.class)
    public SmsService smsService(SmsProvider smsProvider, AuditLogService auditLogService, SmsProperties properties) {
        return new SmsService(smsProvider, auditLogService, properties.getMaxAttempts(), properties.getRetryBackoffMillis());
    }
}
