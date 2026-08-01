package com.farm2home.common.core.constants;

/** Kafka event-type strings, plus the "_SMS"/"_EMAIL" suffixes notification-service
 *  concatenates onto an event's eventType to build a notification_templates.template_code
 *  lookup key (see NotificationServiceImpl.process()). There deliberately isn't a separate
 *  set of pre-composed "ORDER_CREATED_EMAIL"-style constants here - every real call site
 *  builds that string dynamically (eventType + suffix, exactly like production code does),
 *  so a parallel set of static template-code constants would just be unused documentation.
 *  Keep the EVENT_* values in sync with the Flyway seed data in notification-service. */
public final class EmailTemplateConstants {

    private EmailTemplateConstants() {
    }

    public static final String SMS_SUFFIX = "_SMS";
    public static final String EMAIL_SUFFIX = "_EMAIL";
    public static final String PUSH_SUFFIX = "_PUSH";

    // Set as OrderEvent/CustomerEvent/etc. eventType by producers, and compared against /
    // concatenated with a suffix by notification-service to resolve a template.
    public static final String EVENT_ORDER_CREATED = "ORDER_CREATED";
    public static final String EVENT_CUSTOMER_CREATED = "CUSTOMER_CREATED";
    public static final String EVENT_OTP = "OTP";
    public static final String EVENT_INVENTORY_UPDATED = "INVENTORY_UPDATED";
    public static final String EVENT_DELIVERY_ASSIGNED = "DELIVERY_ASSIGNED";
    public static final String EVENT_DELIVERY_COMPLETED = "DELIVERY_COMPLETED";
    public static final String EVENT_DELIVERY_DELAYED = "DELIVERY_DELAYED";
    public static final String EVENT_PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
    public static final String EVENT_SUBSCRIPTION_CREATED = "SUBSCRIPTION_CREATED";
    public static final String EVENT_SUBSCRIPTION_PAUSED = "SUBSCRIPTION_PAUSED";
    public static final String EVENT_SUBSCRIPTION_RESUMED = "SUBSCRIPTION_RESUMED";
    public static final String EVENT_SUBSCRIPTION_CANCELLED = "SUBSCRIPTION_CANCELLED";
}
