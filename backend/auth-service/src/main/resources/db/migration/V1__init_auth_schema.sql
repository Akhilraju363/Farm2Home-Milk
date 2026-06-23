-- ============================================================
-- Auth Service — Schema: auth
-- ============================================================

CREATE TABLE auth.users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  UNIQUE NOT NULL,
    email         VARCHAR(100) UNIQUE,
    mobile        VARCHAR(15)  UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by    VARCHAR(100),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE auth.roles (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(100),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(100),
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE auth.user_roles (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role_id    UUID      NOT NULL REFERENCES auth.roles(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, role_id)
);

CREATE TABLE auth.otp_verifications (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile     VARCHAR(15) NOT NULL,
    otp        VARCHAR(10) NOT NULL,
    otp_type   VARCHAR(50) NOT NULL,  -- REGISTRATION | LOGIN | FORGOT_PASSWORD
    is_used    BOOLEAN     NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE auth.refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    is_revoked  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_users_mobile    ON auth.users(mobile);
CREATE INDEX idx_users_email     ON auth.users(email);
CREATE INDEX idx_otp_mobile      ON auth.otp_verifications(mobile, otp_type);
CREATE INDEX idx_refresh_user    ON auth.refresh_tokens(user_id);

-- Seed: default roles
INSERT INTO auth.roles (id, name, description) VALUES
    (gen_random_uuid(), 'SUPER_ADMIN',    'Full system access'),
    (gen_random_uuid(), 'FARM_MANAGER',   'Manage cows, production, inventory'),
    (gen_random_uuid(), 'DELIVERY_MANAGER','Manage routes and delivery partners'),
    (gen_random_uuid(), 'DELIVERY_PARTNER','View and update own deliveries'),
    (gen_random_uuid(), 'CUSTOMER',       'Subscribe, order, and pay');
