-- Templates for the CUSTOMER_CREATED event (customer.events topic), published by
-- auth-service on registration.
INSERT INTO notification.notification_templates (template_code, channel, subject, body) VALUES
('CUSTOMER_CREATED_SMS',   'SMS',   NULL, 'Welcome to Farm2Home Milk, {{customer_name}}! Your account has been created.'),
('CUSTOMER_CREATED_EMAIL', 'EMAIL', 'Welcome to Farm2Home Milk', 'Dear {{customer_name}}, welcome to Farm2Home Milk! Your account has been created successfully.');
