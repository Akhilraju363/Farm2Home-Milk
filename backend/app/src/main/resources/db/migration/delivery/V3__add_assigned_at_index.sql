-- ============================================================
-- Delivery Service — index delivery_assignments.assigned_at: already the default sort
-- column and the date-range filter for both /reports and the new /search endpoint.
-- ============================================================

CREATE INDEX idx_assignments_assigned_at ON delivery.delivery_assignments (assigned_at);
