package com.farm2home.common.core.constants;

/** Kafka topic names used across the platform's event-driven flows. */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String ORDER_EVENTS = "order.events";
    public static final String CUSTOMER_EVENTS = "customer.events";
    public static final String OTP_EVENTS = "otp.events";
    public static final String DELIVERY_EVENTS = "delivery.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String INVENTORY_EVENTS = "inventory.events";
    /** Published by subscription-service on subscription lifecycle changes; consumed by
     *  order-service (subscription snapshot sync) and notification-service (Subscription Alerts). */
    public static final String SUBSCRIPTION_EVENTS = "subscription.events";
}
