package com.farm2home.common.core.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Development-only provider: logs the push notification instead of actually sending it - active
 * by default ({@code push.provider} unset, or explicitly {@code logging}), and used by every
 * automated test across every service, so nothing requires real push gateway credentials to run
 * locally or in CI. Always succeeds, so local dev/tests never have to deal with simulated
 * delivery failures - to exercise {@link PushService}'s retry/failure path in a test, provide a
 * different {@link PushProvider} stub/mock instead of relying on this one.
 */
public class LoggingPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushProvider.class);

    @Override
    public String getName() {
        return "LOGGING";
    }

    @Override
    public PushSendResult send(PushMessage message) {
        log.info("[PUSH] To: {} | Event: {} | Title: {} | Body: {}",
                message.recipient(), message.eventType(), message.title(), message.body());
        return PushSendResult.success("logged-" + UUID.randomUUID());
    }
}
