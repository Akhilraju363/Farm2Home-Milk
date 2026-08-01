package com.farm2home.common.core.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Development-only provider: logs the SMS instead of actually sending it - active by default
 * ({@code sms.provider} unset, or explicitly {@code logging}), and used by every automated test
 * across every service, so nothing requires real SMS gateway credentials to run locally or in CI.
 * Always succeeds, so local dev/tests never have to deal with simulated delivery failures - to
 * exercise {@link SmsService}'s retry/failure path in a test, provide a different
 * {@link SmsProvider} stub/mock instead of relying on this one.
 */
public class LoggingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

    @Override
    public String getName() {
        return "LOGGING";
    }

    @Override
    public SmsSendResult send(SmsMessage message) {
        log.info("[SMS] To: {} | Event: {} | Message: {}", message.to(), message.eventType(), message.body());
        return SmsSendResult.success("logged-" + UUID.randomUUID());
    }
}
