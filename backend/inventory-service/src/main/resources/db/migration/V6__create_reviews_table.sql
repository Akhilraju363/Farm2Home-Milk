CREATE TABLE inventory.reviews (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   UUID          NOT NULL REFERENCES inventory.products(id),
    customer_id  UUID          NOT NULL,
    order_id     UUID          NOT NULL,
    rating       SMALLINT      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text  TEXT,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(100),
    is_deleted   BOOLEAN       NOT NULL DEFAULT FALSE
);

-- customer_id/order_id are plain UUID references into customer-service/order-service's own
-- tables (never a cross-schema FK - see products/customers convention elsewhere in this codebase).

CREATE INDEX idx_reviews_product_id  ON inventory.reviews(product_id);
CREATE INDEX idx_reviews_customer_id ON inventory.reviews(customer_id);
CREATE INDEX idx_reviews_order_id    ON inventory.reviews(order_id);

-- One review per customer+order+product, but only while live - a soft-deleted review must not
-- block a fresh one for the same order/product (e.g. after an admin moderates it away).
CREATE UNIQUE INDEX uq_reviews_customer_order_product
    ON inventory.reviews(customer_id, order_id, product_id)
    WHERE is_deleted = FALSE;
