package com.nexusbank.corebanking.domain.exception;

import com.nexusbank.corebanking.domain.model.AccountType;

public class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException(String customerId, AccountType type) {
        super("Conta " + type + " já existe para o cliente: " + customerId);
    }
}
