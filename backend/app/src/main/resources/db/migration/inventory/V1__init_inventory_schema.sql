-- ============================================================
-- Inventory Service — Schema: inventory
-- ============================================================

CREATE TABLE inventory.inventory_items (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    item_name      VARCHAR(100)  NOT NULL,
    item_type      VARCHAR(30)   NOT NULL,  -- FEED | MEDICINE | EQUIPMENT
    quantity       DECIMAL(10,2) NOT NULL DEFAULT 0,
    unit           VARCHAR(20)   NOT NULL,  -- KG | LITRE | PIECE | BOX
    reorder_level  DECIMAL(10,2) NOT NULL DEFAULT 0,
    unit_price     DECIMAL(8,2),
    supplier       VARCHAR(100),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(100),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(100),
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE TABLE inventory.stock_transactions (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id       UUID          NOT NULL REFERENCES inventory.inventory_items(id),
    txn_type      VARCHAR(10)   NOT NULL,   -- IN | OUT
    quantity      DECIMAL(10,2) NOT NULL,
    reason        VARCHAR(100),
    reference_id  UUID,
    transacted_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100)
);

-- Indexes
CREATE INDEX idx_inventory_type    ON inventory.inventory_items(item_type);
CREATE INDEX idx_inventory_reorder ON inventory.inventory_items(reorder_level);
CREATE INDEX idx_stock_txn_item    ON inventory.stock_transactions(item_id);
