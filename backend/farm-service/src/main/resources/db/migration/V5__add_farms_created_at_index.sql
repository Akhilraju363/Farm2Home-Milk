-- ============================================================
-- Farm Service — index farms.created_at: used for the date-range filter on
-- GET /api/v1/farm/search (farms has no status/enum column to index for search).
-- ============================================================

CREATE INDEX idx_farms_created_at ON farm.farms (created_at);
