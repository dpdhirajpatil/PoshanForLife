-- Gamification & Badges: extends prompt 06's patient_programmes. Badges are a
-- small, admin-managed reference table (no per-row FK to a specific
-- challenge/programme — criteriaType+criteriaValue describe a generic
-- milestone, e.g. "complete 3 programmes", not "complete THIS programme").
CREATE TABLE badges (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name           varchar(255) NOT NULL,
    description    text,
    icon_key       varchar(100) NOT NULL,
    criteria_type  varchar(32)  NOT NULL,
    criteria_value integer      NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);

-- One row per patient/badge pair — earning is permanent (no un-earning), and
-- the unique constraint is what makes badge-evaluation idempotent (safe to
-- re-run the same criteria check without awarding duplicates).
CREATE TABLE patient_badges (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_id   uuid        NOT NULL REFERENCES badges (id) ON DELETE CASCADE,
    earned_at  timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_patient_badges_patient_badge UNIQUE (patient_id, badge_id)
);
CREATE INDEX idx_patient_badges_patient ON patient_badges (patient_id);

-- One row per challenge-type patient_programme (enforced in the service
-- layer, not a DB CHECK — checking a sibling row's service_type needs a
-- trigger, not worth it for this). last_logged_date/current_streak together
-- are enough state to derive streaks day-to-day without a separate
-- check-in-history table.
CREATE TABLE challenge_progress (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_programme_id  uuid        NOT NULL UNIQUE REFERENCES patient_programmes (id) ON DELETE CASCADE,
    current_streak        integer     NOT NULL DEFAULT 0,
    longest_streak         integer     NOT NULL DEFAULT 0,
    last_logged_date      date,
    percent_complete       integer     NOT NULL DEFAULT 0,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);
