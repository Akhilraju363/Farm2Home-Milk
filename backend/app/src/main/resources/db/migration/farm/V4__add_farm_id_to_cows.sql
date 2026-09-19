-- ============================================================
-- Farm Service — link cows to the farm they belong to. Nullable: existing
-- cows predate this column and have no farm assignment to backfill.
-- ============================================================

ALTER TABLE farm.cows
    ADD COLUMN farm_id UUID REFERENCES farm.farms(id);

CREATE INDEX idx_farm_cows_farm_id ON farm.cows (farm_id);
