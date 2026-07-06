CREATE TABLE IF NOT EXISTS notifications.notification_dlq (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    topic         VARCHAR(255) NOT NULL,
    payload       TEXT         NOT NULL,
    error_message TEXT,
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    exhausted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_retry_at TIMESTAMPTZ
);
