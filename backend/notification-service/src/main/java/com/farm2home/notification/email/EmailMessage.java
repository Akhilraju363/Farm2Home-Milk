package com.farm2home.notification.email;

/** @param eventType the business event this email is for (e.g. "ORDER_CREATED", "OTP") - carried
 *                    through to the provider and the audit trail purely for context, never
 *                    branched on by {@link EmailProvider} implementations themselves. Mirrors
 *                    {@code com.farm2home.common.core.sms.SmsMessage}. */
public record EmailMessage(String to, String subject, String body, String eventType) {
}
