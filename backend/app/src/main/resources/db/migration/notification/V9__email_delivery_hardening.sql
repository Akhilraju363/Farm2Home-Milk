-- ============================================================
-- Email notifications hardening (see docs/email-notifications.md):
--
-- 1) Idempotency columns for notification_logs - a Kafka redelivery of the same message (consumer
--    restart/rebalance before offset commit) must not double-send SMS/EMAIL/PUSH. source_event_id
--    is the producing event's own entity id (orderId/paymentId/assignmentId/subscriptionId);
--    event_occurred_at is when the source event was published. Both together identify one real
--    occurrence - keying on source_event_id alone would incorrectly suppress a genuinely new
--    occurrence of a recurring event type for the same entity (e.g. a subscription paused,
--    resumed, then paused again). Both are NULL for event types with no entity id at all (OTP,
--    CUSTOMER_CREATED) - those are simply never deduped (see NotificationServiceImpl).
--
--    The partial unique index is the real guarantee (enforced even under concurrent processing);
--    the application-level existsBy... check in NotificationServiceImpl is just the fast path
--    that avoids attempting a duplicate send in the first place.
--
-- 2) PAYMENT_REFUNDED_EMAIL - PaymentServiceImpl already publishes eventType "PAYMENT_REFUNDED"
--    (payment.events) on a full refund, but no template existed on any channel for it - a refund
--    silently generated zero customer notification. Adding EMAIL only, in scope for this task;
--    the same SMS/PUSH gap is pre-existing and out of scope here (see docs/email-notifications.md).
-- ============================================================

ALTER TABLE notification.notification_logs ADD COLUMN source_event_id UUID;
ALTER TABLE notification.notification_logs ADD COLUMN event_occurred_at TIMESTAMP;

CREATE UNIQUE INDEX idx_notif_logs_dedupe
    ON notification.notification_logs (channel, event_type, source_event_id, event_occurred_at)
    WHERE status = 'SENT' AND source_event_id IS NOT NULL AND event_occurred_at IS NOT NULL;

INSERT INTO notification.notification_templates (template_code, channel, subject, body) VALUES
('PAYMENT_REFUNDED_EMAIL', 'EMAIL', 'Payment Refunded - Farm2Home Milk',
 '<!DOCTYPE html><html><body style="margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,Helvetica,sans-serif;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f4;padding:24px 0;"><tr><td align="center"><table role="presentation" width="480" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:8px;overflow:hidden;"><tr><td style="background-color:#2e7d32;padding:20px 24px;"><span style="color:#ffffff;font-size:20px;font-weight:bold;">Farm2Home Milk</span></td></tr><tr><td style="padding:32px 24px;color:#333333;"><p style="font-size:18px;margin:0 0 16px;">Payment Refunded</p><p style="font-size:15px;line-height:1.5;margin:0 0 20px;">Dear {{customer_name}}, your payment for order #{{order_number}} has been refunded.</p><table role="presentation" width="100%" cellpadding="8" cellspacing="0" style="border-collapse:collapse;margin:0 0 20px;background-color:#f9f9f9;border-radius:6px;"><tr><td style="padding:12px;color:#777777;font-size:13px;">Order Number</td><td style="padding:12px;text-align:right;font-weight:bold;font-size:14px;">{{order_number}}</td></tr><tr><td style="padding:12px;color:#777777;font-size:13px;border-top:1px solid #eeeeee;">Reference</td><td style="padding:12px;text-align:right;font-weight:bold;font-size:14px;border-top:1px solid #eeeeee;">{{reference}}</td></tr><tr><td style="padding:12px;color:#777777;font-size:13px;border-top:1px solid #eeeeee;">Amount Refunded</td><td style="padding:12px;text-align:right;font-weight:bold;font-size:16px;color:#2e7d32;border-top:1px solid #eeeeee;">Rs {{amount}}</td></tr></table><p style="font-size:13px;color:#777777;margin:0;">The refund will reflect in your original payment method within a few business days.</p></td></tr><tr><td style="padding:16px 24px;background-color:#fafafa;text-align:center;"><span style="font-size:12px;color:#999999;">This is an automated message from Farm2Home Milk. Please do not reply.</span></td></tr></table></td></tr></table></body></html>')
ON CONFLICT (template_code) DO NOTHING;
