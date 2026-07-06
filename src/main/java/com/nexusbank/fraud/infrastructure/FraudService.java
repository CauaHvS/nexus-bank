package com.nexusbank.fraud.infrastructure;

import com.nexusbank.fraud.FraudApi;
import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.FraudEvaluationRequest;
import com.nexusbank.fraud.application.usecase.EvaluateFraudUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de FraudApi. Participa da transação do chamador (Payments),
 * garantindo que a auditoria é persistida atomicamente com a decisão.
 */
@Service
class FraudService implements FraudApi {

    private final EvaluateFraudUseCase evaluateUseCase;

    FraudService(EvaluateFraudUseCase evaluateUseCase) {
        this.evaluateUseCase = evaluateUseCase;
    }

    @Override
    @Transactional
    public FraudDecision evaluate(FraudEvaluationRequest request) {
        return evaluateUseCase.execute(request);
    }
}
