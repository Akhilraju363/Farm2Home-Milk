-- ============================================================
-- Order Service — Schema: "order"   (reserved keyword → quoted)
-- audit_log: discrete audit trail (order created, status changes)
-- Append-only event log, one row per audited action.
-- ============================================================

CREATE TABLE "order".audit_log (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    action         VARCHAR(50)  NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      VARCHAR(100),
    performed_by   VARCHAR(100),
    correlation_id VARCHAR(64),
    details        TEXT
);

CREATE INDEX idx_order_audit_log_occurred_at ON "order".audit_log (occurred_at);
CREATE INDEX idx_order_audit_log_entity ON "order".audit_log (entity_type, entity_id);
CREATE INDEX idx_order_audit_log_performed_by ON "order".audit_log (performed_by);
