package com.nexusbank.fraud.domain.port.out;

/**
 * Porta de saída para consulta de contexto histórico de transferências.
 * Implementada por adapter que consulta payments.transfers via JdbcTemplate (cross-schema).
 * Elimina dependência de PaymentsApi no EvaluateFraudUseCase, evitando ciclo de módulo.
 */
public interface FraudContextRepository {

    /**
     * Conta quantas transferências o userId realizou nas últimas 24 horas
     * (statuses PENDING, COMPLETED e UNDER_REVIEW).
     */
    int countTransfersLast24h(String userId);

    /**
     * Retorna true se o targetAccountId nunca recebeu uma transferência COMPLETED
     * do userId (destino novo = risco mais alto).
     */
    boolean isNewDestination(String userId, String targetAccountId);
}
