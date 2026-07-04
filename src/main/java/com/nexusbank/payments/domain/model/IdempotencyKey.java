package com.nexusbank.payments.domain.model;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Idempotency key não pode ser vazia");
        if (value.length() > 64)
            throw new IllegalArgumentException("Idempotency key excede 64 caracteres");
    }

    @Override
    public String toString() { return value; }
}
