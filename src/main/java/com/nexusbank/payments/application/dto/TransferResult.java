package com.nexusbank.payments.application.dto;

import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.model.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResult(
        UUID transferId,
        String sourceAccountId,
        String targetAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String idempotencyKey,
        Instant createdAt,
        Instant scheduledFor
) {
    public static TransferResult from(Transfer t) {
        return new TransferResult(
                t.getId().value(),
                t.getSourceAccountId(),
                t.getTargetAccountId(),
                t.getAmount().amount(),
                t.getAmount().currency().name(),
                t.getStatus(),
                t.getIdempotencyKey().value(),
                t.getCreatedAt(),
                t.getScheduledFor()
        );
    }
}
