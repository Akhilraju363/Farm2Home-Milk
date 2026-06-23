-- ============================================================
-- Delivery Service — Schema: delivery
-- ============================================================

CREATE TABLE delivery.delivery_routes (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    route_name   VARCHAR(100) NOT NULL,
    route_code   VARCHAR(20)  UNIQUE NOT NULL,
    area         VARCHAR(100) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    pincode      VARCHAR(10)  NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(100),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE delivery.delivery_partners (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    route_id     UUID         REFERENCES delivery.delivery_routes(id),
    name         VARCHAR(100) NOT NULL,
    mobile       VARCHAR(15)  NOT NULL,
    vehicle_type VARCHAR(50),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(100),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE delivery.delivery_assignments (
    id                  UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID      NOT NULL,
    delivery_partner_id UUID      NOT NULL REFERENCES delivery.delivery_partners(id),
    route_id            UUID      NOT NULL REFERENCES delivery.delivery_routes(id),
    assigned_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    delivered_at        TIMESTAMP,
    status              VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED',
    -- ASSIGNED | OUT_FOR_DELIVERY | DELIVERED | FAILED
    failure_reason      VARCHAR(255),
    delivery_proof      VARCHAR(500),  -- image URL
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_assignments_order    ON delivery.delivery_assignments(order_id);
CREATE INDEX idx_assignments_partner  ON delivery.delivery_assignments(delivery_partner_id);
CREATE INDEX idx_assignments_status   ON delivery.delivery_assignments(status);
CREATE INDEX idx_routes_pincode       ON delivery.delivery_routes(pincode);
