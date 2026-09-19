-- ============================================================
-- Invoice Service — audit_log: shared audit trail table required by
-- common-core's AuditLogService/AuditLogRepository (same convention as every
-- other service in this platform - see farm-service's V3__create_audit_log.sql).
-- ============================================================

CREATE TABLE invoice.audit_log (
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

CREATE INDEX idx_invoice_audit_log_occurred_at ON invoice.audit_log (occurred_at);
CREATE INDEX idx_invoice_audit_log_entity ON invoice.audit_log (entity_type, entity_id);
CREATE INDEX idx_invoice_audit_log_performed_by ON invoice.audit_log (performed_by);
