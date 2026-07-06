-- Índice composto para query de extrato: filtra por account_id e ordena por occurred_at DESC
-- Substitui o uso separado dos dois índices escalares criados na V3.
-- CONCURRENTLY não pode rodar dentro de transação; esta migration usa transação normal
-- (volume baixo em dev). Em produção com tabela grande, executar fora do Flyway.
CREATE INDEX IF NOT EXISTS idx_entries_account_id_occurred_at
    ON corebanking.account_entries(account_id, occurred_at DESC);
