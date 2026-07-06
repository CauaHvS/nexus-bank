package com.nexusbank.payments.application.dto;

import java.math.BigDecimal;

public record InitiateTransferCommand(
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        String currency,
        String type,
        String idempotencyKey,
        String description,
        String authenticatedUserId
) {}
