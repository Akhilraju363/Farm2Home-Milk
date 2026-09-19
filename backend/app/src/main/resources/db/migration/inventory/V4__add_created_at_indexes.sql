-- ============================================================
-- Inventory Service — index created_at on both entities now searchable via
-- GET /api/v1/inventory/products/search and /api/v1/inventory/search
-- (default sort column + date-range filter on both).
-- ============================================================

CREATE INDEX idx_products_created_at ON inventory.products (created_at);
CREATE INDEX idx_inventory_items_created_at ON inventory.inventory_items (created_at);
