package com.nexusbank.corebanking.domain.event;

import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.AccountType;
import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.CustomerId;

import java.time.Instant;

public record AccountOpened(
        AccountId accountId,
        CustomerId customerId,
        String accountNumber,
        AccountType type,
        Currency currency,
        Instant occurredAt) {
}
