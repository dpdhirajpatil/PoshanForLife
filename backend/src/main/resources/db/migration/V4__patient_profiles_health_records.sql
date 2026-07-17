-- Patient-management feature: 1:1 medical profile per patient user, plus a
-- minimal health_records table (extended by the health-records prompt later)
-- so patient stats and the detail Overview are computable now.
-- Note: date_of_birth already lives on users (V3) and is NOT duplicated here.

CREATE TABLE patient_profiles (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid          NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    gender            varchar(16),
    blood_group       varchar(8),
    height_cm         numeric(5, 1),
    emergency_contact varchar(255),
    medical_history   text,
    doctor_notes      text,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE health_records (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id   uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    weight_kg    numeric(5, 1),
    body_fat_pct numeric(4, 1),
    recorded_at  timestamptz   NOT NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_health_records_patient_recorded
    ON health_records (patient_id, recorded_at DESC);
