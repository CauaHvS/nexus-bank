package com.nexusbank.corebanking.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StatementResult(
    List<Entry> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public record Entry(
        UUID entryId,
        String type,
        BigDecimal amount,
        String description,
        Instant occurredAt,
        BigDecimal balanceAfter
    ) {}
}
