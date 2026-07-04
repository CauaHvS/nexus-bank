package com.nexusbank.corebanking.application.dto;

import com.nexusbank.corebanking.domain.model.AccountStatus;
import com.nexusbank.corebanking.domain.model.AccountType;
import com.nexusbank.corebanking.domain.model.Currency;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountView(
        UUID accountId,
        String accountNumber,
        String agency,
        AccountType type,
        Currency currency,
        BigDecimal balance,
        AccountStatus status) {
}
