-- Mobile self-signup: a lead's converted_patient_id now also links a brand
-- new LEAD-role account back to its Lead row from the moment it's created
-- (not only after staff convert it to PATIENT), so it's a true one-to-one
-- relationship end to end. Enforce that at the DB level.

CREATE UNIQUE INDEX idx_leads_converted_patient_unique
    ON leads (converted_patient_id)
    WHERE converted_patient_id IS NOT NULL;
