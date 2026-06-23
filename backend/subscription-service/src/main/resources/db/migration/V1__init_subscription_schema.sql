-- ============================================================
-- Subscription Service — Schema: subscription
-- ============================================================

CREATE TABLE subscription.subscriptions (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id    UUID          NOT NULL,
    milk_type      VARCHAR(50)   NOT NULL,   -- FULL_CREAM | TONED | DOUBLE_TONED | SKIMMED
    quantity       DECIMAL(5, 2) NOT NULL,   -- in liters
    schedule_type  VARCHAR(30)   NOT NULL,   -- DAILY | ALTERNATE_DAY | WEEKLY
    delivery_days  VARCHAR(50),              -- e.g. MON,WED,FRI for WEEKLY
    start_date     DATE          NOT NULL,
    end_date       DATE,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PAUSED | CANCELLED | EXPIRED
    pause_start    DATE,
    pause_end      DATE,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(100),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(100),
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

-- Indexes
CREATE INDEX idx_subscriptions_customer ON subscription.subscriptions(customer_id);
CREATE INDEX idx_subscriptions_status   ON subscription.subscriptions(status);
CREATE INDEX idx_subscriptions_dates    ON subscription.subscriptions(start_date, end_date);
