package com.nexusbank.fraud.domain.port.out;

import com.nexusbank.fraud.domain.model.FraudEvaluation;

import java.util.Optional;

/**
 * Porta de saída para persistência de avaliações de risco.
 */
public interface FraudAuditRepository {
    void save(FraudEvaluation evaluation);
    Optional<FraudEvaluation> findByTransferId(String transferId);
}
