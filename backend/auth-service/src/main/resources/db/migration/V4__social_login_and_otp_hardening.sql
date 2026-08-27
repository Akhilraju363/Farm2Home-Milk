-- ============================================================
-- Google Sign-In + Mobile OTP Login hardening (see AUTH_SOCIAL_OTP_PROGRESS.md).
--
-- 1) auth.user_identities - links an external identity provider (Google today; the "provider"
--    column exists purely so this table doesn't need to be recreated if a second provider is ever
--    added, NOT as scaffolding for one - see AuthServiceImpl.googleAuth()) to an existing
--    auth.users row. provider_user_id stores the provider's own stable subject id (Google's
--    "sub" claim), never the email - see AUTH_SOCIAL_OTP_PROGRESS.md's account-linking section for
--    why. A user can have at most one identity per provider (unique (provider, provider_user_id)
--    already prevents two users claiming the same external identity; the app layer additionally
--    never lets one user link a given provider twice).
--
-- 2) otp_verifications hardening - the existing column stored the raw 6-digit OTP in plaintext
--    (VARCHAR(10), long enough only for the digits themselves). It now stores a BCrypt hash
--    (varchar(255), same encoder already used for user passwords - see OtpService), and a new
--    `attempts` counter enables a bounded-attempts limit on verify() that did not exist before
--    (previously: unlimited verification attempts against a 5-minute-lived code).
-- ============================================================

CREATE TABLE auth.user_identities (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider          VARCHAR(20)  NOT NULL,   -- GOOGLE
    provider_user_id  VARCHAR(255) NOT NULL,   -- Google's "sub" claim - stable, never the email
    email             VARCHAR(100),
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    display_name      VARCHAR(150),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_user_identities_user ON auth.user_identities(user_id);

ALTER TABLE auth.otp_verifications ALTER COLUMN otp TYPE VARCHAR(255);
ALTER TABLE auth.otp_verifications ADD COLUMN attempts INT NOT NULL DEFAULT 0;
