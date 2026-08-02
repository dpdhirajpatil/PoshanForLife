-- Phone-number (OTP) login and signup for PATIENT/LEAD self-service accounts.
-- Staff accounts (ADMIN/DOCTOR) are unaffected: they stay email+password and
-- are still created only by an admin, never through this flow.

-- An OTP-only account has no email and no password, so both columns lose their
-- NOT NULL. (password_hash isn't called out in the feature spec, but a
-- phone-signup account is defined there as having password_hash = null, which
-- the original V2 NOT NULL would reject outright.) The pre-existing UNIQUE on
-- email still holds — Postgres allows many NULLs in a unique index, so any
-- number of phone-only accounts coexist without colliding.
ALTER TABLE users
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN phone_verified boolean NOT NULL DEFAULT false;

-- Only *verified* phones are exclusive. An unverified phone can sit on an
-- abandoned signup attempt forever without blocking the real owner from
-- claiming that number later.
CREATE UNIQUE INDEX idx_users_phone_verified_unique
    ON users (phone)
    WHERE phone_verified = true;

-- The core invariant: every account keeps at least one confirmed way to
-- identify itself. Without this, clearing an email off a password-only
-- account would silently strand it with no usable login route.
ALTER TABLE users
    ADD CONSTRAINT chk_users_has_identifier
        CHECK (email IS NOT NULL OR (phone IS NOT NULL AND phone_verified = true));

-- One row per OTP issued. The code itself is bcrypt-hashed, never stored raw,
-- so a database leak doesn't hand over live login codes.
CREATE TABLE phone_otps (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- E.164, e.g. +919876543210 (13 chars); 20 leaves room for longer countries.
    phone      varchar(20)  NOT NULL,
    otp_hash   varchar(255) NOT NULL,
    purpose    varchar(16)  NOT NULL,
    expires_at timestamptz  NOT NULL,
    -- Wrong guesses against this one code; at 5 the row is dead and the caller
    -- must request a fresh OTP.
    attempts   integer      NOT NULL DEFAULT 0,
    verified   boolean      NOT NULL DEFAULT false,
    -- Set for ADD_PHONE only, linking the request to the already-authenticated
    -- user attaching the number. NULL for SIGNUP/LOGIN.
    user_id    uuid         REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- Verification always looks up the newest unverified row for a phone+purpose.
CREATE INDEX idx_phone_otps_lookup
    ON phone_otps (phone, purpose, created_at DESC);
