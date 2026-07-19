-- Catalogue feature: three parallel item tables (programmes, sessions,
-- challenges) sharing one field shape, plus:
--  * catalogue_service_codes — registry giving DB-level uniqueness of
--    service_code ACROSS all three tables (one row per catalogue item).
--  * patient_programmes — minimal assignments table (extended by the orders
--    prompt later) so catalogue deletion can already be blocked while a
--    patient is actively assigned to an item.

CREATE TABLE programmes (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(255)   NOT NULL,
    service_code    varchar(64)    NOT NULL UNIQUE,
    type            varchar(100)   NOT NULL,
    price_inr       numeric(10, 2) NOT NULL,
    description     text,
    cover_image_url varchar(1000),
    status          varchar(16)    NOT NULL DEFAULT 'DRAFT',
    duration_weeks  integer        NOT NULL,
    created_by      uuid           NOT NULL REFERENCES users (id),
    created_at      timestamptz    NOT NULL DEFAULT now(),
    updated_at      timestamptz    NOT NULL DEFAULT now()
);

CREATE TABLE sessions (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name             varchar(255)   NOT NULL,
    service_code     varchar(64)    NOT NULL UNIQUE,
    type             varchar(100)   NOT NULL,
    price_inr        numeric(10, 2) NOT NULL,
    description      text,
    cover_image_url  varchar(1000),
    status           varchar(16)    NOT NULL DEFAULT 'DRAFT',
    duration_minutes integer        NOT NULL,
    created_by       uuid           NOT NULL REFERENCES users (id),
    created_at       timestamptz    NOT NULL DEFAULT now(),
    updated_at       timestamptz    NOT NULL DEFAULT now()
);

CREATE TABLE challenges (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name             varchar(255)   NOT NULL,
    service_code     varchar(64)    NOT NULL UNIQUE,
    type             varchar(100)   NOT NULL,
    price_inr        numeric(10, 2) NOT NULL,
    description      text,
    cover_image_url  varchar(1000),
    status           varchar(16)    NOT NULL DEFAULT 'DRAFT',
    duration_days    integer        NOT NULL,
    goal_description text           NOT NULL,
    created_by       uuid           NOT NULL REFERENCES users (id),
    created_at       timestamptz    NOT NULL DEFAULT now(),
    updated_at       timestamptz    NOT NULL DEFAULT now()
);

CREATE TABLE catalogue_service_codes (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code       varchar(64) NOT NULL,
    item_type  varchar(16) NOT NULL,
    item_id    uuid        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (item_type, item_id)
);

-- Case-insensitive global uniqueness — the service layer checks first for a
-- friendly error; this index closes the race between concurrent creates.
CREATE UNIQUE INDEX uq_catalogue_service_codes_code
    ON catalogue_service_codes (lower(code));

CREATE TABLE patient_programmes (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_type  varchar(16) NOT NULL,
    item_id    uuid        NOT NULL,
    status     varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_patient_programmes_item
    ON patient_programmes (item_type, item_id, status);
