-- ============================================================
-- Milk Production Service — Schema: production
-- ============================================================

CREATE TABLE production.milk_production (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    cow_id           UUID          NOT NULL,
    collection_date  DATE          NOT NULL,
    session          VARCHAR(10)   NOT NULL,   -- MORNING | EVENING
    quantity_liters  DECIMAL(6, 2) NOT NULL,
    fat_percentage   DECIMAL(4, 2),
    snf_percentage   DECIMAL(4, 2),
    quality_grade    VARCHAR(10),              -- A | B | C
    collected_by     VARCHAR(100),
    notes            VARCHAR(255),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(100),
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    UNIQUE (cow_id, collection_date, session)
);

-- Indexes
CREATE INDEX idx_production_cow      ON production.milk_production(cow_id);
CREATE INDEX idx_production_date     ON production.milk_production(collection_date);
CREATE INDEX idx_production_session  ON production.milk_production(session);
