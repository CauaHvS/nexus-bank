package com.nexusbank.fraud.adapter.out.persistence;

import com.nexusbank.fraud.domain.port.out.FraudContextRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Adapter que consulta payments.transfers diretamente via JdbcTemplate
 * para obter contexto de fraude sem criar dependência de módulo em PaymentsApi.
 *
 * Nota arquitetural: leitura cruzada de schema (fraud lê payments.transfers)
 * é aceitável no monólito. Na extração do Fraud para microsserviço, essa leitura
 * viraria chamada REST à PaymentsApi. O isolamento está no código, não no banco.
 */
@Component
class FraudContextPersistenceAdapter implements FraudContextRepository {

    private final JdbcTemplate jdbc;

    FraudContextPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int countTransfersLast24h(String userId) {
        String sql = """
                SELECT COUNT(*) FROM payments.transfers
                WHERE source_account_id IN (
                    SELECT id FROM corebanking.accounts WHERE customer_id = ?::uuid
                )
                AND created_at >= ?
                AND status IN ('PENDING', 'COMPLETED', 'UNDER_REVIEW')
                """;
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        Integer count = jdbc.queryForObject(sql, Integer.class, userId,
                Timestamp.from(since));
        return count != null ? count : 0;
    }

    @Override
    public boolean isNewDestination(String userId, String targetAccountId) {
        String sql = """
                SELECT COUNT(*) FROM payments.transfers
                WHERE source_account_id IN (
                    SELECT id FROM corebanking.accounts WHERE customer_id = ?::uuid
                )
                AND target_account_id = ?::uuid
                AND status = 'COMPLETED'
                """;
        Integer count = jdbc.queryForObject(sql, Integer.class, userId, targetAccountId);
        return count == null || count == 0;
    }
}
