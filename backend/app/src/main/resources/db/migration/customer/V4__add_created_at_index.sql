-- ============================================================
-- Customer Service — index created_at: now used for both the default sort order
-- and the date-range filter on GET /api/v1/customers/search and /reports.
-- ============================================================

CREATE INDEX idx_customers_created_at ON customer.customers (created_at);
