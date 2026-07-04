CREATE TABLE IF NOT EXISTS payments.transfers (
    id                UUID          PRIMARY KEY,
    source_account_id UUID          NOT NULL,
    target_account_id UUID          NOT NULL,
    amount            NUMERIC(19,2) NOT NULL,
    currency          VARCHAR(3)    NOT NULL DEFAULT 'BRL',
    type              VARCHAR(20)   NOT NULL DEFAULT 'INTERNAL',
    status            VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    idempotency_key   VARCHAR(64)   NOT NULL UNIQUE,
    failure_reason    TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_transfers_idempotency ON payments.transfers(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_transfers_status ON payments.transfers(status);
