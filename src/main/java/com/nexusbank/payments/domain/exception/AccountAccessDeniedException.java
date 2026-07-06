package com.nexusbank.payments.domain.exception;

public class AccountAccessDeniedException extends RuntimeException {
    public AccountAccessDeniedException(String accountId) {
        super("Acesso negado à conta: " + accountId);
    }
}
