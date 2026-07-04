CREATE TABLE IF NOT EXISTS corebanking.accounts (
    id             UUID          PRIMARY KEY,
    customer_id    UUID          NOT NULL,
    account_number VARCHAR(20)   NOT NULL UNIQUE,
    agency         VARCHAR(10)   NOT NULL DEFAULT '0001',
    type           VARCHAR(20)   NOT NULL,
    currency       VARCHAR(3)    NOT NULL DEFAULT 'BRL',
    balance        NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version        BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_accounts_customer_id ON corebanking.accounts(customer_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_customer_type
    ON corebanking.accounts(customer_id, type) WHERE status = 'ACTIVE';
