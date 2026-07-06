package com.nexusbank.fraud.domain.model;

import java.math.BigDecimal;

/**
 * Contexto enriquecido de uma transferência para avaliação de risco.
 * Imutável — construído pelo EvaluateFraudUseCase antes de aplicar as regras.
 */
public record FraudContext(
        String transferId,
        String userId,
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        String paymentType,
        int transferCountLast24h,
        boolean isNewDestination
) {}
