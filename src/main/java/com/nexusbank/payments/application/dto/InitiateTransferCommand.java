package com.nexusbank.payments.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InitiateTransferCommand(
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        String currency,
        String type,
        String idempotencyKey,
        String description,
        String authenticatedUserId,
        Instant scheduledFor
) {
    /**
     * Construtor de compatibilidade para código existente sem agendamento.
     */
    public InitiateTransferCommand(String sourceAccountId, String targetAccountId,
                                   BigDecimal amount, String currency, String type,
                                   String idempotencyKey, String description,
                                   String authenticatedUserId) {
        this(sourceAccountId, targetAccountId, amount, currency, type,
                idempotencyKey, description, authenticatedUserId, null);
    }
}
