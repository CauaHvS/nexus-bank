package com.nexusbank.corebanking.domain.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String id) {
        super("Conta não encontrada: " + id);
    }
}
