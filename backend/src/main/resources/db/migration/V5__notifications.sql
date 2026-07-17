-- Minimal notifications store — created here so assignments can notify the
-- doctor; extended by the notifications feature prompt (delivery, prefs, etc.).

CREATE TABLE notifications (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       varchar(64)  NOT NULL,
    message    text         NOT NULL,
    is_read    boolean      NOT NULL DEFAULT false,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read);
