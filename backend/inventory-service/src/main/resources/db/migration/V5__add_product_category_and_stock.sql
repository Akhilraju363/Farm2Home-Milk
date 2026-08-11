-- ============================================================
-- Product Category: a proper relational domain replacing the free-text `category` column that
-- previously sat directly on products (see ProductCategory entity). Distinct from InventoryItem,
-- which represents farm-supply stock (feed/medicine/equipment) - a different business concept
-- that Product intentionally does NOT couple to (see Product.stockQuantity below).
-- ============================================================

CREATE TABLE inventory.product_categories (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(50)  NOT NULL UNIQUE,
    description  TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(100),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_product_categories_active ON inventory.product_categories(is_active);

-- Backfill: one category row per distinct free-text value already in use, so no existing
-- product loses its categorization when category_id replaces the old text column below.
INSERT INTO inventory.product_categories (name)
SELECT DISTINCT category FROM inventory.products WHERE category IS NOT NULL AND category <> '';

ALTER TABLE inventory.products ADD COLUMN category_id UUID REFERENCES inventory.product_categories(id);

UPDATE inventory.products p
SET category_id = c.id
FROM inventory.product_categories c
WHERE p.category = c.name;

-- The free-text column is now fully superseded by the category_id relationship above (see
-- ProductSpecifications.hasKeyword, updated to search the joined category name instead) -
-- dropped rather than left redundant.
DROP INDEX IF EXISTS inventory.idx_products_category;
ALTER TABLE inventory.products DROP COLUMN category;
CREATE INDEX idx_products_category_id ON inventory.products(category_id);

-- ============================================================
-- Stock tracking, native to the sellable Product domain - a deliberate design choice, not an
-- oversight: Product and InventoryItem represent different concepts (sellable catalog item vs.
-- internal farm supply), so Product owns its own stock columns rather than referencing
-- inventory_items.
-- ============================================================

ALTER TABLE inventory.products ADD COLUMN stock_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory.products ADD COLUMN minimum_stock_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE inventory.products ADD CONSTRAINT chk_products_stock_non_negative CHECK (stock_quantity >= 0);
ALTER TABLE inventory.products ADD CONSTRAINT chk_products_min_stock_non_negative CHECK (minimum_stock_quantity >= 0);

-- Unit of sale (L/ML/KG/G/PACK/DOZEN/PIECE - see ProductUnit). Existing products (if any)
-- predate this concept; default them to PIECE so the column can be made NOT NULL without
-- destroying any row. New products must specify a real unit going forward (enforced at the
-- request-validation layer, not here).
ALTER TABLE inventory.products ADD COLUMN unit VARCHAR(10);
UPDATE inventory.products SET unit = 'PIECE' WHERE unit IS NULL;
ALTER TABLE inventory.products ALTER COLUMN unit SET NOT NULL;
