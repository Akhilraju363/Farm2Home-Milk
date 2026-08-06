-- ============================================================
-- Templates for events that already publish today but had no template on one or more
-- channels (see NotificationServiceImpl.process() - a missing template just silently skips
-- that channel, it isn't an error). PAYMENT_FAILED had none at all; SUBSCRIPTION_* and
-- DELIVERY_DELAYED had PUSH only; DELIVERY_OUT_FOR_DELIVERY had none at all.
--
-- ON CONFLICT DO NOTHING: a couple of these template_codes (e.g. DELIVERY_DELAYED_EMAIL,
-- OTP_EMAIL) already exist in some environments from ad-hoc manual inserts made outside any
-- tracked migration - this keeps the migration safe to apply regardless of that drift, rather
-- than failing on the unique constraint.
-- ============================================================

INSERT INTO notification.notification_templates (template_code, channel, subject, body) VALUES
('PAYMENT_FAILED_SMS',              'SMS',   NULL, 'Payment of Rs {{amount}} for order #{{order_number}} could not be processed. Reference: {{reference}}.'),
('PAYMENT_FAILED_EMAIL',            'EMAIL', 'Payment Failed - Farm2Home Milk',
    'Dear {{customer_name}}, your payment of Rs {{amount}} for order #{{order_number}} could not be processed. Reference: {{reference}}. Please try again.'),
('PAYMENT_FAILED_PUSH',             'PUSH',  'Payment Failed',
    'Payment of Rs {{amount}} for order #{{order_number}} could not be processed.'),

('SUBSCRIPTION_CREATED_SMS',        'SMS',   NULL, 'Your Farm2Home Milk subscription for {{quantity}}L {{milk_type}} is now active.'),
('SUBSCRIPTION_CREATED_EMAIL',      'EMAIL', 'Subscription Started - Farm2Home Milk',
    'Dear {{customer_name}}, your subscription for {{quantity}}L {{milk_type}} milk is now active.'),

('SUBSCRIPTION_PAUSED_SMS',         'SMS',   NULL, 'Your {{quantity}}L {{milk_type}} subscription has been paused.'),
('SUBSCRIPTION_PAUSED_EMAIL',       'EMAIL', 'Subscription Paused - Farm2Home Milk',
    'Dear {{customer_name}}, your {{quantity}}L {{milk_type}} subscription has been paused.'),

('SUBSCRIPTION_RESUMED_SMS',        'SMS',   NULL, 'Your {{quantity}}L {{milk_type}} subscription is active again.'),
('SUBSCRIPTION_RESUMED_EMAIL',      'EMAIL', 'Subscription Resumed - Farm2Home Milk',
    'Dear {{customer_name}}, your {{quantity}}L {{milk_type}} subscription is active again.'),

('SUBSCRIPTION_CANCELLED_SMS',      'SMS',   NULL, 'Your {{quantity}}L {{milk_type}} subscription has been cancelled.'),
('SUBSCRIPTION_CANCELLED_EMAIL',    'EMAIL', 'Subscription Cancelled - Farm2Home Milk',
    'Dear {{customer_name}}, your {{quantity}}L {{milk_type}} subscription has been cancelled.'),

('DELIVERY_DELAYED_SMS',            'SMS',   NULL, 'Your order #{{order_number}} delivery is delayed: {{reason}}.'),
('DELIVERY_DELAYED_EMAIL',          'EMAIL', 'Delivery Delayed - Farm2Home Milk',
    'Dear {{customer_name}}, your order #{{order_number}} delivery is delayed: {{reason}}.'),

('DELIVERY_OUT_FOR_DELIVERY_SMS',   'SMS',   NULL, 'Your order #{{order_number}} is out for delivery with {{partner_name}}. Expected: {{expected_time}}.'),
('DELIVERY_OUT_FOR_DELIVERY_EMAIL', 'EMAIL', 'Order Out for Delivery - Farm2Home Milk',
    'Dear {{customer_name}}, your order #{{order_number}} is out for delivery with {{partner_name}}. Expected: {{expected_time}}.'),
('DELIVERY_OUT_FOR_DELIVERY_PUSH',  'PUSH',  'Out for Delivery',
    'Your order #{{order_number}} is out for delivery with {{partner_name}}. Expected: {{expected_time}}.')
ON CONFLICT (template_code) DO NOTHING;
