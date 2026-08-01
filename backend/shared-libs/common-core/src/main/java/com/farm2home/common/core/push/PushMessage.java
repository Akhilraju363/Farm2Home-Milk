package com.farm2home.common.core.push;

/** @param recipient a push-addressable identifier - in this codebase, the customer's id
 *                    (see notification-service's PushService wiring for why: unlike SMS/EMAIL,
 *                    there is no device-token registry, and every event already carries a
 *                    customerId, so push is addressed by customer rather than by device)
 *  @param eventType  the business event this push is for (e.g. "ORDER_CREATED") - carried
 *                     through to the provider and the audit trail purely for context */
public record PushMessage(String recipient, String title, String body, String eventType) {
}
