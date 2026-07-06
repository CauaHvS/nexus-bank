package com.nexusbank.fraud.domain.model;

import com.nexusbank.fraud.FraudDecision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resultado imutável da avaliação de risco de uma transferência.
 * Criado pelo método de fábrica evaluate() após somar os scores das regras.
 */
public class FraudEvaluation {

    private final UUID evaluationId;
    private final String transferId;
    private final String userId;
    private final int score;
    private final FraudDecision decision;
    private final List<String> triggeredRules;
    private final Instant evaluatedAt;

    private FraudEvaluation(UUID evaluationId, String transferId, String userId,
                            int score, FraudDecision decision, List<String> triggeredRules,
                            Instant evaluatedAt) {
        this.evaluationId = evaluationId;
        this.transferId = transferId;
        this.userId = userId;
        this.score = score;
        this.decision = decision;
        this.triggeredRules = List.copyOf(triggeredRules);
        this.evaluatedAt = evaluatedAt;
    }

    /**
     * Cria nova avaliação calculando a decisão a partir do score total.
     * score < 30: APPROVED | 30-69: SUSPICIOUS | >= 70: BLOCKED
     */
    public static FraudEvaluation evaluate(String transferId, String userId,
                                           int score, List<String> triggeredRules) {
        FraudDecision decision;
        if (score >= 70)      decision = FraudDecision.BLOCKED;
        else if (score >= 30) decision = FraudDecision.SUSPICIOUS;
        else                  decision = FraudDecision.APPROVED;

        return new FraudEvaluation(UUID.randomUUID(), transferId, userId,
                score, decision, triggeredRules, Instant.now());
    }

    /** Reconstitui do banco sem gerar novo UUID. */
    public static FraudEvaluation reconstitute(UUID evaluationId, String transferId,
                                               String userId, int score, FraudDecision decision,
                                               List<String> triggeredRules, Instant evaluatedAt) {
        return new FraudEvaluation(evaluationId, transferId, userId, score,
                decision, triggeredRules, evaluatedAt);
    }

    public UUID getEvaluationId()           { return evaluationId; }
    public String getTransferId()           { return transferId; }
    public String getUserId()               { return userId; }
    public int getScore()                   { return score; }
    public FraudDecision getDecision()      { return decision; }
    public List<String> getTriggeredRules() { return triggeredRules; }
    public Instant getEvaluatedAt()         { return evaluatedAt; }
}
