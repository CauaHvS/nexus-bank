package com.nexusbank.fraud.application.dto;

import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.domain.model.FraudEvaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de resposta para avaliações de fraude — exposto pelo FraudController.
 */
public record FraudEvaluationView(
        UUID evaluationId,
        String transferId,
        String userId,
        int score,
        FraudDecision decision,
        List<String> triggeredRules,
        Instant evaluatedAt
) {
    public static FraudEvaluationView from(FraudEvaluation evaluation) {
        return new FraudEvaluationView(
                evaluation.getEvaluationId(),
                evaluation.getTransferId(),
                evaluation.getUserId(),
                evaluation.getScore(),
                evaluation.getDecision(),
                evaluation.getTriggeredRules(),
                evaluation.getEvaluatedAt()
        );
    }
}
