package com.nexusbank.payments.domain.event;

import com.nexusbank.payments.domain.model.TransferId;

import java.time.Instant;

public record TransferFailed(
        TransferId transferId,
        String reason,
        Instant occurredAt) {}
