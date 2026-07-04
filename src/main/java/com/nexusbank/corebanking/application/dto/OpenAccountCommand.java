package com.nexusbank.corebanking.application.dto;

import com.nexusbank.corebanking.domain.model.AccountType;
import com.nexusbank.corebanking.domain.model.Currency;

public record OpenAccountCommand(String customerId, AccountType type, Currency currency) {
}
