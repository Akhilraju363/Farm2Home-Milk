-- One cart per customer (created lazily on first add-to-cart). CartItem has no price snapshot -
-- price/name/image/unit are resolved live from inventory-service at cart-read time (they aren't
-- final until checkout), matching the "never trust a stored/frontend price" rule this schema's
-- own OrderItem enforces at order-creation time instead.
CREATE TABLE "order".carts (
    id          UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID      NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE "order".cart_items (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id     UUID          NOT NULL REFERENCES "order".carts(id) ON DELETE CASCADE,
    product_id  UUID          NOT NULL,
    quantity    DECIMAL(5, 2) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    -- Adding a product already in the cart increases this row's quantity rather than creating a
    -- second CartItem for the same product (see CartServiceImpl.addItem).
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart ON "order".cart_items(cart_id);
