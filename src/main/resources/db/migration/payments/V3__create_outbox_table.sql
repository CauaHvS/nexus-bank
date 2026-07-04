CREATE TABLE IF NOT EXISTS payments.outbox (
    id             BIGSERIAL     PRIMARY KEY,
    aggregate_id   VARCHAR(64)   NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    payload        TEXT          NOT NULL,
    published      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON payments.outbox(published) WHERE published = FALSE;
