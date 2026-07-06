package com.nexusbank.fraud;

import java.math.BigDecimal;

/**
 * Dados passados pelo módulo Payments para avaliação de risco.
 */
public record FraudEvaluationRequest(
        String transferId,
        String userId,
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        String paymentType
) {}
