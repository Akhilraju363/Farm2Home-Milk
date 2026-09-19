-- DPDP Act 2023 compliance: per-purpose consent records and data-rights/grievance intake.
-- See DPDP_PROGRESS.md at the repo root for the design rationale.

CREATE TABLE customer.consent_records (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id    UUID          NOT NULL,
    purpose        VARCHAR(40)   NOT NULL,
    granted        BOOLEAN       NOT NULL DEFAULT FALSE,
    notice_version VARCHAR(50),
    source         VARCHAR(50),
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(255),
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(100),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(100)
);

-- One current row per customer+purpose - re-granting/withdrawing updates this row in place
-- (history is preserved via the platform's existing generic audit log, not by row versioning
-- here).
CREATE UNIQUE INDEX uq_consent_records_customer_purpose ON customer.consent_records(customer_id, purpose);
CREATE INDEX idx_consent_records_customer_id ON customer.consent_records(customer_id);

CREATE TABLE customer.data_rights_requests (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id       UUID,
    requester_name    VARCHAR(150)  NOT NULL,
    requester_contact VARCHAR(150)  NOT NULL,
    request_type      VARCHAR(30)   NOT NULL,
    details           TEXT,
    status            VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    resolution_notes  TEXT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(100)
);

CREATE INDEX idx_data_rights_requests_customer_id ON customer.data_rights_requests(customer_id);
CREATE INDEX idx_data_rights_requests_status      ON customer.data_rights_requests(status);
