-- ============================================================
-- Order Service — Schema: order
-- ============================================================

CREATE TABLE "order".orders (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number    VARCHAR(30)   UNIQUE NOT NULL,
    customer_id     UUID          NOT NULL,
    subscription_id UUID,
    order_date      DATE          NOT NULL,
    order_type      VARCHAR(20)   NOT NULL DEFAULT 'SUBSCRIPTION',  -- SUBSCRIPTION | ONE_TIME
    total_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    status          VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    -- PENDING | ASSIGNED | OUT_FOR_DELIVERY | DELIVERED | CANCELLED
    notes           VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(100),
    is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE TABLE "order".order_items (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID          NOT NULL REFERENCES "order".orders(id) ON DELETE CASCADE,
    milk_type   VARCHAR(50)   NOT NULL,
    quantity    DECIMAL(5, 2) NOT NULL,
    unit_price  DECIMAL(8, 2) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_orders_customer      ON "order".orders(customer_id);
CREATE INDEX idx_orders_status        ON "order".orders(status);
CREATE INDEX idx_orders_date          ON "order".orders(order_date);
CREATE INDEX idx_orders_subscription  ON "order".orders(subscription_id);
CREATE INDEX idx_order_items_order    ON "order".order_items(order_id);

-- Sequence for order_number
CREATE SEQUENCE "order".order_number_seq START 100000;
