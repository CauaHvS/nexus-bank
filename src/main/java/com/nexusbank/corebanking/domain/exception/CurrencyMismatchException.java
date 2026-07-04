package com.nexusbank.corebanking.domain.exception;

import com.nexusbank.corebanking.domain.model.Currency;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency a, Currency b) {
        super("Moedas incompatíveis: " + a + " e " + b);
    }
}
