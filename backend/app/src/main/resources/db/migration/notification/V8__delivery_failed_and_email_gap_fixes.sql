-- ============================================================
-- DeliveryAssignmentServiceImpl publishes eventType "DELIVERY_FAILED" (delivery.events) whenever
-- an assignment transitions to FAILED, but no template existed on any channel for it - a failed
-- delivery silently generated zero SMS/EMAIL/PUSH to the customer. failureReason is always set on
-- that transition (DeliveryAssignmentServiceImpl requires it) and maps onto {{reason}}, the same
-- placeholder DELIVERY_DELAYED already uses (see NotificationServiceImpl.buildPayload()).
--
-- Also fills the EMAIL gap on DELIVERY_ASSIGNED/DELIVERY_COMPLETED, which - unlike every other
-- delivery event - had SMS+PUSH but no EMAIL template.
--
-- ON CONFLICT DO NOTHING for the same reason as V6: safe to reapply if a template_code was
-- already inserted manually outside a tracked migration in some environment.
-- ============================================================

INSERT INTO notification.notification_templates (template_code, channel, subject, body) VALUES
('DELIVERY_FAILED_SMS',   'SMS',   'Delivery Unsuccessful', 'We could not deliver your order #{{order_number}}: {{reason}}. We will contact you to reschedule.'),
('DELIVERY_FAILED_EMAIL', 'EMAIL', 'Delivery Unsuccessful - Farm2Home Milk',
    'Dear {{customer_name}}, we were unable to deliver your order #{{order_number}}. Reason: {{reason}}. Our team will reach out shortly to reschedule your delivery.'),
('DELIVERY_FAILED_PUSH',  'PUSH',  'Delivery Unsuccessful',
    'We could not deliver your order #{{order_number}}: {{reason}}.'),

('DELIVERY_ASSIGNED_EMAIL',  'EMAIL', 'Your Order is on the Way - Farm2Home Milk',
    'Dear {{customer_name}}, your order #{{order_number}} has been assigned to {{partner_name}}. Expected delivery: {{expected_time}}.'),
('DELIVERY_COMPLETED_EMAIL', 'EMAIL', 'Order Delivered - Farm2Home Milk',
    'Dear {{customer_name}}, your order #{{order_number}} has been delivered. Thank you for choosing Farm2Home Milk!')
ON CONFLICT (template_code) DO NOTHING;
