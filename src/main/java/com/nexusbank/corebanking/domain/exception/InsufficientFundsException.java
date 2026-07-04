package com.nexusbank.corebanking.domain.exception;

import com.nexusbank.corebanking.domain.model.Money;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Money balance, Money requested) {
        super("Saldo insuficiente: saldo " + balance + ", solicitado " + requested);
    }
}
