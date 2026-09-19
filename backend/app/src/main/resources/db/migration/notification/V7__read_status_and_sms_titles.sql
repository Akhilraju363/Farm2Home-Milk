-- ============================================================
-- Read/unread tracking for Notification History - the only genuinely new per-instance state
-- needed (Type and Priority are pure functions of event_type, computed by
-- NotificationClassifier at read time rather than stored, so they don't need columns here).
-- ============================================================

ALTER TABLE notification.notification_logs ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill a real title for every SMS template - SMS has no separate title/subject field in
-- the channel itself, so these were left NULL when first seeded, but Notification History wants
-- every entry to have a real Title, not just the EMAIL/PUSH ones that already had a subject.
UPDATE notification.notification_templates SET subject = 'Order Confirmed'        WHERE template_code = 'ORDER_CREATED_SMS'              AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Delivery Assigned'      WHERE template_code = 'DELIVERY_ASSIGNED_SMS'          AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Delivered'              WHERE template_code = 'DELIVERY_COMPLETED_SMS'         AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Payment Received'       WHERE template_code = 'PAYMENT_SUCCESS_SMS'            AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Subscription Renewed'   WHERE template_code = 'SUBSCRIPTION_RENEWAL_SMS'       AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Welcome to Farm2Home'   WHERE template_code = 'CUSTOMER_CREATED_SMS'           AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Delivery Delayed'       WHERE template_code = 'DELIVERY_DELAYED_SMS'           AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Out for Delivery'       WHERE template_code = 'DELIVERY_OUT_FOR_DELIVERY_SMS'  AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Payment Failed'         WHERE template_code = 'PAYMENT_FAILED_SMS'             AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Subscription Started'   WHERE template_code = 'SUBSCRIPTION_CREATED_SMS'       AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Subscription Paused'    WHERE template_code = 'SUBSCRIPTION_PAUSED_SMS'        AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Subscription Resumed'   WHERE template_code = 'SUBSCRIPTION_RESUMED_SMS'       AND subject IS NULL;
UPDATE notification.notification_templates SET subject = 'Subscription Cancelled' WHERE template_code = 'SUBSCRIPTION_CANCELLED_SMS'     AND subject IS NULL;
