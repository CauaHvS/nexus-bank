package com.nexusbank.fraud;

/**
 * Resultado da avaliação de risco de fraude.
 * APPROVED: score < 30 — fluxo normal.
 * SUSPICIOUS: 30 <= score < 70 — transferência entra em UNDER_REVIEW.
 * BLOCKED: score >= 70 — transferência é bloqueada (FraudBlockedException lançada).
 */
public enum FraudDecision {
    APPROVED,
    SUSPICIOUS,
    BLOCKED
}
