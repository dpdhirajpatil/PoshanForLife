-- Lead-specific streak tracking (AN-22): a LEAD user has no PatientProgramme
-- to hang a challenge_progress row off (Leads don't have programmes by
-- definition), so this is a lightweight, standalone per-lead-user streak
-- instead of extending challenge_progress. Evaluated against the same
-- badges.criteria_type = 'streak_days' rows via BadgeEvaluationService
-- (unmodified — it already takes a generic User, not a PatientProgramme).
CREATE TABLE lead_streaks (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_user_id      uuid        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    current_streak    integer     NOT NULL DEFAULT 0,
    longest_streak    integer     NOT NULL DEFAULT 0,
    last_logged_date  date,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
