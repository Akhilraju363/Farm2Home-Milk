-- ============================================================
-- Delivery Service — delivery_locations: append-only GPS history submitted by a
-- delivery partner while an assignment is OUT_FOR_DELIVERY. "Current location" is
-- just the most recent row per assignment (see the index below), not a separate table.
-- ============================================================

CREATE TABLE delivery.delivery_locations (
    id                      UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_assignment_id  UUID             NOT NULL REFERENCES delivery.delivery_assignments(id) ON DELETE CASCADE,
    delivery_partner_id     UUID             NOT NULL,
    latitude                DOUBLE PRECISION NOT NULL,
    longitude               DOUBLE PRECISION NOT NULL,
    accuracy                DOUBLE PRECISION,
    speed                   DOUBLE PRECISION,
    heading                 DOUBLE PRECISION,
    recorded_at             TIMESTAMP        NOT NULL
);

-- Serves both "current location" (ORDER BY recorded_at DESC LIMIT 1) and "history"
-- (paginated, same ordering) without a full table scan.
CREATE INDEX idx_delivery_locations_assignment_recorded
    ON delivery.delivery_locations (delivery_assignment_id, recorded_at DESC);
