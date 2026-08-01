-- ============================================================
-- Auth Service — extend audit_log with the enterprise-audit field set
-- (userId/username/serviceName, old/new value, HTTP context, success/failure).
-- ============================================================

ALTER TABLE auth.audit_log
    ADD COLUMN user_id        VARCHAR(64),
    ADD COLUMN username       VARCHAR(150),
    ADD COLUMN service_name   VARCHAR(60),
    ADD COLUMN old_value      TEXT,
    ADD COLUMN new_value      TEXT,
    ADD COLUMN ip_address     VARCHAR(64),
    ADD COLUMN request_uri    VARCHAR(255),
    ADD COLUMN http_method    VARCHAR(10),
    ADD COLUMN success        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN failure_reason TEXT;
