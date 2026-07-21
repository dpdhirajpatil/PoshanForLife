-- Reports feature (manual records + AI-extracted InBody PDFs) plus the
-- HealthRecord extension it writes into on a successful extraction (the full
-- InBody-derived shape belongs to the health-records feature, prompt 10, but
-- Reports needs it now so the upload pipeline has somewhere to upsert into).

CREATE TABLE reports (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id            uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                 varchar(255)  NOT NULL,
    type                  varchar(16)   NOT NULL,
    notes                 varchar(5000),
    status                varchar(16)   NOT NULL DEFAULT 'PENDING',
    -- Private-bucket object path (poshan-reports), NOT a public URL — the API
    -- layer mints a fresh short-lived signed URL from this on every read.
    file_url              varchar(1000),
    parsed_data           jsonb,
    confidence            varchar(8),
    extracted_field_count integer,
    extraction_method     varchar(32),
    created_by            uuid          NOT NULL REFERENCES users (id),
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_reports_patient_created ON reports (patient_id, created_at DESC);
CREATE INDEX idx_reports_status ON reports (status);

-- health_records: add the InBody-derived metrics + the upsert-by-day key.
ALTER TABLE health_records
    ADD COLUMN record_date             date,
    ADD COLUMN skeletal_muscle_mass_kg numeric(5, 1),
    ADD COLUMN bmi                     numeric(4, 1),
    ADD COLUMN visceral_fat_level      numeric(4, 1),
    ADD COLUMN body_water_l            numeric(5, 1),
    ADD COLUMN protein_kg              numeric(4, 1),
    ADD COLUMN mineral_kg              numeric(4, 2),
    ADD COLUMN basal_metabolic_rate    numeric(6, 1),
    ADD COLUMN source                  varchar(16) NOT NULL DEFAULT 'MANUAL';

UPDATE health_records SET record_date = (recorded_at AT TIME ZONE 'UTC')::date WHERE record_date IS NULL;

ALTER TABLE health_records ALTER COLUMN record_date SET NOT NULL;
ALTER TABLE health_records ADD CONSTRAINT uq_health_records_patient_date UNIQUE (patient_id, record_date);
