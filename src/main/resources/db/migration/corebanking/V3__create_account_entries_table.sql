CREATE TABLE IF NOT EXISTS corebanking.account_entries (
    id             UUID          PRIMARY KEY,
    account_id     UUID          NOT NULL REFERENCES corebanking.accounts(id),
    type           VARCHAR(10)   NOT NULL,  -- DEBIT | CREDIT
    amount         NUMERIC(19,2) NOT NULL,
    description    VARCHAR(255)  NOT NULL,
    balance_after  NUMERIC(19,2) NOT NULL,
    occurred_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_entries_account_id ON corebanking.account_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_entries_occurred_at ON corebanking.account_entries(occurred_at DESC);
