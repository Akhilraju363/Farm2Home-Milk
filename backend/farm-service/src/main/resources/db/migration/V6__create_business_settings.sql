-- Singleton table (enforced by singleton_guard below, always id=1) holding the Farm2Home
-- business's own delivery origin - distinct from farm.farms, which is the multi-row registered
-- supplier-farm registry and is not the company's delivery hub.
CREATE TABLE farm.business_settings (
    id                  SMALLINT      PRIMARY KEY DEFAULT 1,
    farm_latitude       NUMERIC(10,8) NOT NULL,
    farm_longitude      NUMERIC(11,8) NOT NULL,
    delivery_radius_km  NUMERIC(6,2)  NOT NULL,
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(100),
    CONSTRAINT singleton_guard CHECK (id = 1)
);

INSERT INTO farm.business_settings (id, farm_latitude, farm_longitude, delivery_radius_km, updated_by)
VALUES (1, 13.62708825, 78.96885066, 10.00, 'system');
