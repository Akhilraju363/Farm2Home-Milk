-- ============================================================
-- Invoice Service — Schema: invoice
-- ============================================================

CREATE TABLE invoice.invoices (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(30)   UNIQUE NOT NULL,
    order_id       UUID          UNIQUE NOT NULL,
    customer_id    UUID          NOT NULL,
    subtotal       DECIMAL(10,2) NOT NULL,
    total_amount   DECIMAL(10,2) NOT NULL,
    issue_date     DATE          NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(100),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(100),
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_invoices_customer ON invoice.invoices(customer_id);
CREATE INDEX idx_invoices_order    ON invoice.invoices(order_id);
CREATE INDEX idx_invoices_date     ON invoice.invoices(issue_date);

-- Sequence for invoice_number (INV-YYYY-NNNNNN), same convention as order-service's
-- order_number_seq (ORD-YYYY-NNNNNN).
CREATE SEQUENCE invoice.invoice_number_seq START 1;
