package com.nexusbank.payments.domain.exception;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(String id) {
        super("Transferência não encontrada: " + id);
    }
}
