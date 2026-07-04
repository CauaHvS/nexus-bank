package com.nexusbank.corebanking.domain.event;

import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.Money;

import java.time.Instant;

public record BalanceUpdated(
        AccountId accountId,
        Money newBalance,
        Instant occurredAt) {
}
