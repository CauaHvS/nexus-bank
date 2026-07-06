package com.nexusbank.fraud;

/**
 * API pública do módulo Fraud.
 * Chamada pelo módulo Payments antes do débito em cada transferência.
 */
public interface FraudApi {

    /**
     * Avalia o risco de fraude de uma transferência.
     * Lança FraudBlockedException se a decisão for BLOCKED.
     * Retorna FraudDecision (APPROVED ou SUSPICIOUS) nos demais casos.
     */
    FraudDecision evaluate(FraudEvaluationRequest request);
}
