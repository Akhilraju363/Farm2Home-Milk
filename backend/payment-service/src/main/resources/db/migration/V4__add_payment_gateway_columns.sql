-- ============================================================
-- Payment Service — real payment gateway integration
-- ============================================================

ALTER TABLE payment.payments
    ADD COLUMN gateway_order_id  VARCHAR(100),
    ADD COLUMN gateway_payment_id VARCHAR(100),
    ADD COLUMN gateway_refund_id VARCHAR(100);

CREATE INDEX idx_payments_gateway_order   ON payment.payments(gateway_order_id);
CREATE INDEX idx_payments_gateway_payment ON payment.payments(gateway_payment_id);
