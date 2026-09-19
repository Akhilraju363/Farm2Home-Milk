CREATE TABLE inventory.products (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100)  NOT NULL,
    description  TEXT,
    category     VARCHAR(50),
    price        DECIMAL(8, 2) NOT NULL,
    image_url    VARCHAR(255),
    is_active    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(100),
    is_deleted   BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_products_category ON inventory.products(category);
CREATE INDEX idx_products_active   ON inventory.products(is_active);
