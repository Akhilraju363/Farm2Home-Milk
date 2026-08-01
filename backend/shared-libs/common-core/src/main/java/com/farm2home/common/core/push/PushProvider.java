package com.farm2home.common.core.push;

/**
 * Provider abstraction over a real push notification gateway (Firebase Cloud Messaging, APNs,
 * OneSignal, etc.) so callers never depend on a specific vendor's SDK or API shape - only this
 * interface and {@link PushService}, which wraps it with retries, graceful failure handling, and
 * audit publishing. Mirrors {@code SmsProvider} in the sibling {@code sms} package exactly, and
 * payment-service's {@code PaymentGatewayProvider} before it.
 *
 * Exactly one implementation is active per environment, selected via the {@code push.provider}
 * property (see {@code PushAutoConfiguration}): {@code logging} (default) for local development.
 * A future real provider plugs in the same way: implement this interface, add a
 * {@code @ConditionalOnProperty(name = "push.provider", havingValue = "...")}-gated
 * {@code @Bean}, and set {@code PUSH_PROVIDER} in the deploying environment - no change needed
 * anywhere this interface is consumed.
 */
public interface PushProvider {

    /** Identifies the active provider for diagnostics/audit trails (e.g. "LOGGING", "FCM"). */
    String getName();

    /** Sends a single push notification. Implementations should return a failed
     *  {@link PushSendResult} for provider-reported failures rather than throwing where
     *  possible; {@link PushService} treats a thrown exception and a failed result identically
     *  (both trigger a retry), so either is safe to use. */
    PushSendResult send(PushMessage message);
}
