CREATE TABLE farm.farms (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_name    VARCHAR(150) NOT NULL,
    owner_name   VARCHAR(150) NOT NULL,
    location     VARCHAR(255),
    description  TEXT,
    image_url    VARCHAR(255),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(100),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_farms_owner ON farm.farms(owner_name);
