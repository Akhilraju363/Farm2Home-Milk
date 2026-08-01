package com.farm2home.common.core.sms;

/** @param eventType the business event this SMS is for (e.g. "OTP", "ORDER_CREATED") - carried
 *                    through to the provider and the audit trail purely for context, never
 *                    branched on by {@link SmsProvider} implementations themselves. */
public record SmsMessage(String to, String body, String eventType) {
}
