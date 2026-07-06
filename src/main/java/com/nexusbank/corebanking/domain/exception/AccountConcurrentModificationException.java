package com.nexusbank.corebanking.domain.exception;

public class AccountConcurrentModificationException extends RuntimeException {

    public AccountConcurrentModificationException(String accountId) {
        super("Conflito de concorrência na conta: " + accountId);
    }
}
