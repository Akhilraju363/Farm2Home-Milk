-- ============================================================
-- Production Service — audit_log: discrete audit trail (production record
-- created, milk batch updated). Append-only event log, one row per audited action.
-- ============================================================

CREATE TABLE production.audit_log (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    action         VARCHAR(50)  NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      VARCHAR(100),
    performed_by   VARCHAR(100),
    correlation_id VARCHAR(64),
    details        TEXT,
    user_id        VARCHAR(64),
    username       VARCHAR(150),
    service_name   VARCHAR(60),
    old_value      TEXT,
    new_value      TEXT,
    ip_address     VARCHAR(64),
    request_uri    VARCHAR(255),
    http_method    VARCHAR(10),
    success        BOOLEAN      NOT NULL DEFAULT TRUE,
    failure_reason TEXT
);

CREATE INDEX idx_production_audit_log_occurred_at ON production.audit_log (occurred_at);
CREATE INDEX idx_production_audit_log_entity ON production.audit_log (entity_type, entity_id);
CREATE INDEX idx_production_audit_log_performed_by ON production.audit_log (performed_by);
