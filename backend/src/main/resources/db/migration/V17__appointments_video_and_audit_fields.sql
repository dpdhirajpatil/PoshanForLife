-- Extends V15's appointments table for the Admin/Doctor portal Appointments
-- feature: video-call scaffolding (unused until a provider is integrated —
-- video_room_id stays null) and created_by audit tracking (who booked it —
-- PATIENT for a self-booked slot, DOCTOR/ADMIN when booked on the patient's
-- behalf). Nullable since existing rows predate this column; no backfill
-- possible (the original booking caller isn't recoverable from other data).
ALTER TABLE appointments
    ADD COLUMN is_video       boolean NOT NULL DEFAULT false,
    ADD COLUMN video_room_id  varchar(255),
    ADD COLUMN created_by     uuid REFERENCES users (id);
