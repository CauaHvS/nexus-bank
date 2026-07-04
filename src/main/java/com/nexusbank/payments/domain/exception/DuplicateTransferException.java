package com.nexusbank.payments.domain.exception;

public class DuplicateTransferException extends RuntimeException {
    public DuplicateTransferException(String idempotencyKey) {
        super("Transferência já processada com esta chave de idempotência: " + idempotencyKey);
    }
}
