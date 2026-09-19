-- ============================================================
-- Customer Service — Schema: customer
-- ============================================================

CREATE TABLE customer.customers (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_code VARCHAR(20)  UNIQUE NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    mobile        VARCHAR(15)  UNIQUE NOT NULL,
    email         VARCHAR(100) UNIQUE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | INACTIVE | SUSPENDED
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE customer.customer_addresses (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID         NOT NULL REFERENCES customer.customers(id) ON DELETE CASCADE,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city          VARCHAR(100) NOT NULL,
    state         VARCHAR(100) NOT NULL,
    pincode       VARCHAR(10)  NOT NULL,
    latitude      DECIMAL(10, 8),
    longitude     DECIMAL(11, 8),
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Indexes
CREATE INDEX idx_customers_mobile      ON customer.customers(mobile);
CREATE INDEX idx_customers_status      ON customer.customers(status);
CREATE INDEX idx_addresses_customer    ON customer.customer_addresses(customer_id);
CREATE INDEX idx_addresses_pincode     ON customer.customer_addresses(pincode);

-- Sequence for customer_code
CREATE SEQUENCE customer.customer_code_seq START 1000;
