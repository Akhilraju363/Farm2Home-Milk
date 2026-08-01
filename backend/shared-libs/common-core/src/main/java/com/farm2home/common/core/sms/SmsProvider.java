package com.farm2home.common.core.sms;

/**
 * Provider abstraction over a real SMS gateway (Twilio, MSG91, AWS SNS, etc.) so callers
 * ({@code OtpService} in auth-service; order/delivery/payment notifications in
 * notification-service) never depend on a specific vendor's SDK or API shape - only this
 * interface and {@link SmsService}, which wraps it with retries, graceful failure handling, and
 * audit publishing.
 *
 * Exactly one implementation is active per environment, selected via the {@code sms.provider}
 * property (see {@code SmsAutoConfiguration}): {@code logging} (default) for local development,
 * where no real SMS gateway is wired up yet. A future real provider plugs in the same way the
 * payment-service's {@code PaymentGatewayProvider} does for Razorpay: implement this interface,
 * add a {@code @ConditionalOnProperty(name = "sms.provider", havingValue = "...")}-gated
 * {@code @Bean}, and set {@code SMS_PROVIDER} in the deploying environment - no change needed
 * anywhere this interface is consumed.
 */
public interface SmsProvider {

    /** Identifies the active provider for diagnostics/audit trails (e.g. "LOGGING", "TWILIO"). */
    String getName();

    /** Sends a single SMS. Implementations should return a failed {@link SmsSendResult} for
     *  provider-reported failures rather than throwing where possible; {@link SmsService} treats
     *  a thrown exception and a failed result identically (both trigger a retry), so either is
     *  safe to use. */
    SmsSendResult send(SmsMessage message);
}
