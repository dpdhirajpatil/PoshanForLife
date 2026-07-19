-- Patient Programmes (service assignment) feature.
--
-- 1. patient_programmes grows from its minimal V6 shape into the full
--    assignment entity: the generic item_id column is split into
--    programme_id / session_id / challenge_id (exactly one set, matching
--    service_type), plus dates, price, notes and assignment audit fields.
--    The catalogue columns intentionally have NO foreign keys: prompt 06's
--    verified rule allows deleting an ARCHIVED catalogue item even while
--    assignments reference it — the service layer validates existence and
--    published status at assignment time instead.
-- 2. orders + transactions are created with their full field shape from
--    prompts 07/08 (only the endpoints arrive with those prompts), because
--    assigning a service already creates an order and, for priced items, an
--    activation transaction.
-- 3. invoice_counters backs the concurrency-safe per-month invoice sequence
--    (INV-{YYYYMM}-{NNNN}) — incremented under SELECT ... FOR UPDATE.

ALTER TABLE patient_programmes RENAME COLUMN item_type TO service_type;

ALTER TABLE patient_programmes
    ADD COLUMN programme_id       uuid,
    ADD COLUMN session_id         uuid,
    ADD COLUMN challenge_id       uuid,
    ADD COLUMN start_date         date,
    ADD COLUMN end_date           date,
    ADD COLUMN price_inr          numeric(10, 2),
    ADD COLUMN notes              text,
    ADD COLUMN assigned_by        uuid REFERENCES users (id),
    ADD COLUMN assigned_doctor_id uuid REFERENCES users (id);

UPDATE patient_programmes
SET programme_id = CASE WHEN service_type = 'PROGRAMME' THEN item_id END,
    session_id   = CASE WHEN service_type = 'SESSION' THEN item_id END,
    challenge_id = CASE WHEN service_type = 'CHALLENGE' THEN item_id END,
    start_date   = created_at::date,
    price_inr    = 0;

ALTER TABLE patient_programmes DROP COLUMN item_id;

ALTER TABLE patient_programmes
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN price_inr SET NOT NULL;

ALTER TABLE patient_programmes
    ADD CONSTRAINT chk_patient_programmes_one_item CHECK (
        (service_type = 'PROGRAMME' AND programme_id IS NOT NULL
             AND session_id IS NULL AND challenge_id IS NULL)
        OR (service_type = 'SESSION' AND session_id IS NOT NULL
             AND programme_id IS NULL AND challenge_id IS NULL)
        OR (service_type = 'CHALLENGE' AND challenge_id IS NOT NULL
             AND programme_id IS NULL AND session_id IS NULL)
    );

-- (the old idx_patient_programmes_item was dropped automatically with item_id)
CREATE INDEX idx_patient_programmes_programme ON patient_programmes (programme_id) WHERE programme_id IS NOT NULL;
CREATE INDEX idx_patient_programmes_session ON patient_programmes (session_id) WHERE session_id IS NOT NULL;
CREATE INDEX idx_patient_programmes_challenge ON patient_programmes (challenge_id) WHERE challenge_id IS NOT NULL;
CREATE INDEX idx_patient_programmes_patient ON patient_programmes (patient_id);

CREATE TABLE orders (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id           uuid           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    patient_programme_id uuid           REFERENCES patient_programmes (id),
    amount_inr           numeric(10, 2) NOT NULL,
    status               varchar(16)    NOT NULL DEFAULT 'ACTIVE',
    payment_status       varchar(16)    NOT NULL DEFAULT 'PENDING',
    notes                text,
    created_by           uuid           NOT NULL REFERENCES users (id),
    created_at           timestamptz    NOT NULL DEFAULT now(),
    updated_at           timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_patient ON orders (patient_id);
CREATE INDEX idx_orders_patient_programme ON orders (patient_programme_id);

CREATE TABLE transactions (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   varchar(32)    NOT NULL UNIQUE,
    invoice_number   varchar(20)    NOT NULL UNIQUE,
    transaction_type varchar(16)    NOT NULL,
    payment_type     varchar(16)    NOT NULL,
    price_inr        numeric(10, 2) NOT NULL,
    discount_inr     numeric(10, 2) NOT NULL DEFAULT 0,
    amount_inr       numeric(10, 2) NOT NULL,
    credit_charged   numeric(10, 2) NOT NULL DEFAULT 0,
    source           varchar(32)    NOT NULL DEFAULT 'admin',
    order_id         uuid           NOT NULL REFERENCES orders (id),
    patient_id       uuid           NOT NULL REFERENCES users (id),
    created_by       uuid           NOT NULL REFERENCES users (id),
    created_at       timestamptz    NOT NULL DEFAULT now(),
    updated_at       timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_order ON transactions (order_id);
CREATE INDEX idx_transactions_patient ON transactions (patient_id);

CREATE TABLE invoice_counters (
    month_key  varchar(6) PRIMARY KEY,
    next_value integer    NOT NULL
);
