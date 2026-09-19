-- ============================================================
-- Notification Service — Schema: notification
-- ============================================================

CREATE TABLE notification.notification_templates (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code VARCHAR(50)  UNIQUE NOT NULL,
    channel       VARCHAR(20)  NOT NULL,   -- SMS | EMAIL | PUSH
    subject       VARCHAR(255),
    body          TEXT         NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE notification.notification_logs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id  UUID         NOT NULL,
    channel       VARCHAR(20)  NOT NULL,   -- SMS | EMAIL | PUSH
    event_type    VARCHAR(50)  NOT NULL,
    -- ORDER_CREATED | DELIVERY_ASSIGNED | DELIVERY_COMPLETED | PAYMENT_SUCCESS | SUBSCRIPTION_RENEWAL
    recipient     VARCHAR(255) NOT NULL,   -- mobile or email
    subject       VARCHAR(255),
    message       TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    failure_reason VARCHAR(500),
    sent_at       TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_notif_logs_recipient   ON notification.notification_logs(recipient_id);
CREATE INDEX idx_notif_logs_event       ON notification.notification_logs(event_type);
CREATE INDEX idx_notif_logs_status      ON notification.notification_logs(status);

-- Seed default templates
INSERT INTO notification.notification_templates (template_code, channel, subject, body) VALUES
('ORDER_CREATED_SMS',       'SMS',   NULL, 'Dear customer, your order #{{order_number}} has been placed. Amount: Rs {{amount}}.'),
('DELIVERY_ASSIGNED_SMS',   'SMS',   NULL, 'Your order #{{order_number}} is assigned to {{partner_name}}. Expected delivery: {{expected_time}}.'),
('DELIVERY_COMPLETED_SMS',  'SMS',   NULL, 'Your order #{{order_number}} has been delivered. Thank you for choosing Farm2Home Milk!'),
('PAYMENT_SUCCESS_SMS',     'SMS',   NULL, 'Payment of Rs {{amount}} received for order #{{order_number}}. Reference: {{reference}}.'),
('SUBSCRIPTION_RENEWAL_SMS','SMS',   NULL, 'Your Farm2Home Milk subscription for {{quantity}}L {{milk_type}} has been renewed.'),
('ORDER_CREATED_EMAIL',     'EMAIL', 'Order Placed - Farm2Home Milk', 'Dear {{customer_name}}, your order #{{order_number}} has been confirmed.'),
('PAYMENT_SUCCESS_EMAIL',   'EMAIL', 'Payment Confirmed - Farm2Home Milk', 'Dear {{customer_name}}, payment of Rs {{amount}} received.');
