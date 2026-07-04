package com.nexusbank.corebanking.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceView(UUID accountId, BigDecimal balance, String currency, Instant updatedAt) {}
