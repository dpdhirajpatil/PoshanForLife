-- User-management feature: profile fields on users + doctor-patient assignments.

ALTER TABLE users
    ADD COLUMN phone         varchar(32),
    ADD COLUMN avatar_url    varchar(512),
    ADD COLUMN date_of_birth date,
    ADD COLUMN is_active     boolean NOT NULL DEFAULT true,
    ADD COLUMN notification_prefs jsonb NOT NULL DEFAULT
        '{"inbodyReport": true, "patientAssigned": true, "processingErrors": true, "systemAnnouncements": true}';

CREATE TABLE doctor_patients (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id  uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    patient_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_doctor_patient UNIQUE (doctor_id, patient_id)
);

CREATE INDEX idx_doctor_patients_doctor_id ON doctor_patients (doctor_id);
CREATE INDEX idx_doctor_patients_patient_id ON doctor_patients (patient_id);
