-- ============================================================
-- Farm Service — Schema: farm
-- ============================================================

CREATE TABLE farm.cows (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_number    VARCHAR(20)  UNIQUE NOT NULL,
    cow_name      VARCHAR(100),
    breed         VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    purchase_date DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SICK | SOLD | DECEASED
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE farm.vaccinations (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cow_id          UUID         NOT NULL REFERENCES farm.cows(id) ON DELETE CASCADE,
    vaccine_name    VARCHAR(100) NOT NULL,
    administered_at DATE         NOT NULL,
    next_due_date   DATE,
    administered_by VARCHAR(100),
    notes           VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(100),
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE farm.health_records (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cow_id      UUID         NOT NULL REFERENCES farm.cows(id) ON DELETE CASCADE,
    record_date DATE         NOT NULL,
    condition   VARCHAR(50)  NOT NULL,   -- HEALTHY | SICK | RECOVERING | CRITICAL
    symptoms    VARCHAR(500),
    treatment   VARCHAR(500),
    vet_name    VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(100),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(100),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Indexes
CREATE INDEX idx_cows_tag_number  ON farm.cows(tag_number);
CREATE INDEX idx_cows_status      ON farm.cows(status);
CREATE INDEX idx_vaccinations_cow ON farm.vaccinations(cow_id);
CREATE INDEX idx_health_cow       ON farm.health_records(cow_id);
CREATE INDEX idx_health_date      ON farm.health_records(record_date);
