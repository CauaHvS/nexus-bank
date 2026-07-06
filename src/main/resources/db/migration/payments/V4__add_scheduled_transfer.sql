ALTER TABLE payments.transfers ADD COLUMN scheduled_for TIMESTAMP WITH TIME ZONE NULL;

CREATE INDEX idx_transfers_scheduled_for ON payments.transfers(scheduled_for) WHERE status = 'SCHEDULED';
