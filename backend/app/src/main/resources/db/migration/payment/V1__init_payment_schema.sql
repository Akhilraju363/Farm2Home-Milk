-- ============================================================
-- Payment Service — Schema: payment
-- ============================================================

CREATE TABLE payment.payments (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID          NOT NULL,
    customer_id       UUID          NOT NULL,
    payment_reference VARCHAR(100)  UNIQUE,
    amount            DECIMAL(10,2) NOT NULL,
    payment_method    VARCHAR(30)   NOT NULL,
    payment_status    VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    gateway_response  TEXT,
    paid_at           TIMESTAMP,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(100),
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE TABLE payment.wallets (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID          UNIQUE NOT NULL,
    balance     DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE payment.wallet_transactions (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id        UUID          NOT NULL REFERENCES payment.wallets(id),
    transaction_type VARCHAR(20)   NOT NULL,
    amount           DECIMAL(10,2) NOT NULL,
    reference_id     UUID,
    description      VARCHAR(255),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order      ON payment.payments(order_id);
CREATE INDEX idx_payments_customer   ON payment.payments(customer_id);
CREATE INDEX idx_payments_status     ON payment.payments(payment_status);
CREATE INDEX idx_payments_ref        ON payment.payments(payment_reference);
CREATE INDEX idx_wallets_customer    ON payment.wallets(customer_id);
CREATE INDEX idx_wallet_tx_wallet    ON payment.wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_tx_ref       ON payment.wallet_transactions(reference_id);
