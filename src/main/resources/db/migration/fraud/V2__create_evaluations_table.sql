CREATE TABLE fraud.evaluations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id     VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    score           INTEGER     NOT NULL,
    decision        VARCHAR(20) NOT NULL,
    triggered_rules TEXT        NOT NULL,
    evaluated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_evaluations_transfer ON fraud.evaluations(transfer_id);
CREATE INDEX idx_fraud_evaluations_user     ON fraud.evaluations(user_id, evaluated_at DESC);
