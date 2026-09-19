-- Lets an order item reference a real inventory-service Product instead of only the legacy
-- milk_type enum, so a customer can order a specific catalog product from the Shop directly
-- (previously there was no link at all between the Product catalog and Order/OrderItem).
--
-- product_id is a plain UUID reference, not a foreign key - inventory-service owns that table in
-- its own schema/service boundary, same convention as every other cross-service reference in this
-- codebase (customerId, etc.). product_name is snapshotted at order time, same as unit_price
-- already was for milk_type items - order history must keep showing what was actually ordered
-- even if the product is later renamed or removed from the catalog.

ALTER TABLE "order".order_items ALTER COLUMN milk_type DROP NOT NULL;
ALTER TABLE "order".order_items ADD COLUMN product_id UUID;
ALTER TABLE "order".order_items ADD COLUMN product_name VARCHAR(150);

-- Every item is exactly one kind - never both, never neither.
ALTER TABLE "order".order_items ADD CONSTRAINT chk_order_items_exactly_one_reference
    CHECK ((milk_type IS NOT NULL AND product_id IS NULL) OR (milk_type IS NULL AND product_id IS NOT NULL));

CREATE INDEX idx_order_items_product_id ON "order".order_items(product_id);
