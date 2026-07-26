-- Appointments feature (AN-07): a patient booking a slot with their assigned
-- practitioner. duration_minutes/status mirror the orders table's shape;
-- ON DELETE CASCADE on patient_id matches orders (a deleted patient's
-- appointments go with them), practitioner_id has no cascade since a
-- practitioner account is never hard-deleted (soft-deactivated only).
CREATE TABLE appointments (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    practitioner_id  uuid        NOT NULL REFERENCES users (id),
    scheduled_at     timestamptz NOT NULL,
    duration_minutes integer     NOT NULL DEFAULT 30,
    status           varchar(16) NOT NULL DEFAULT 'SCHEDULED',
    notes            text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_appointments_patient ON appointments (patient_id);
CREATE INDEX idx_appointments_practitioner_scheduled ON appointments (practitioner_id, scheduled_at);
