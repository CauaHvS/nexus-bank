package com.nexusbank.payments.domain.event;

import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.payments.domain.model.IdempotencyKey;
import com.nexusbank.payments.domain.model.TransferId;

import java.time.Instant;

public record TransferInitiated(
        TransferId transferId,
        String sourceAccountId,
        String targetAccountId,
        Money amount,
        IdempotencyKey idempotencyKey,
        Instant occurredAt) {}
