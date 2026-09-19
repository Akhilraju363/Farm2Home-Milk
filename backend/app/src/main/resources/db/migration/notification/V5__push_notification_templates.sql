-- ============================================================
-- Push notification templates: Order Updates, Delivery Status,
-- Payment Confirmation, Subscription Alerts (see NotificationServiceImpl.process(),
-- which now dispatches PUSH alongside SMS/EMAIL for every event with a customerId).
--
-- subject doubles as the push notification's title (see NotificationServiceImpl.dispatch()'s
-- PUSH case, which passes the rendered subject through to PushService as the title).
-- ============================================================

INSERT INTO notification.notification_templates (template_code, channel, subject, body) VALUES
('ORDER_CREATED_PUSH',          'PUSH', 'Order Confirmed',
    'Your order #{{order_number}} has been placed. Amount: Rs {{amount}}.'),
('DELIVERY_ASSIGNED_PUSH',      'PUSH', 'Out for Delivery Soon',
    'Your order #{{order_number}} is assigned to {{partner_name}}. Expected delivery: {{expected_time}}.'),
('DELIVERY_COMPLETED_PUSH',     'PUSH', 'Delivered',
    'Your order #{{order_number}} has been delivered. Thank you for choosing Farm2Home Milk!'),
('DELIVERY_DELAYED_PUSH',       'PUSH', 'Delivery Delayed',
    'Your order #{{order_number}} delivery is delayed: {{reason}}.'),
('PAYMENT_SUCCESS_PUSH',        'PUSH', 'Payment Received',
    'Payment of Rs {{amount}} received for order #{{order_number}}. Reference: {{reference}}.'),
('SUBSCRIPTION_CREATED_PUSH',   'PUSH', 'Subscription Started',
    'Your subscription for {{quantity}}L {{milk_type}} milk is now active.'),
('SUBSCRIPTION_PAUSED_PUSH',    'PUSH', 'Subscription Paused',
    'Your {{quantity}}L {{milk_type}} subscription has been paused.'),
('SUBSCRIPTION_RESUMED_PUSH',   'PUSH', 'Subscription Resumed',
    'Your {{quantity}}L {{milk_type}} subscription is active again.'),
('SUBSCRIPTION_CANCELLED_PUSH', 'PUSH', 'Subscription Cancelled',
    'Your {{quantity}}L {{milk_type}} subscription has been cancelled.');
