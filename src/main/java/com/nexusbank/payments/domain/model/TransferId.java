package com.nexusbank.payments.domain.model;

import java.util.UUID;

public record TransferId(UUID value) {
    public TransferId {
        if (value == null) throw new IllegalArgumentException("TransferId não pode ser nulo");
    }

    public static TransferId generate() { return new TransferId(UUID.randomUUID()); }
    public static TransferId of(String v) { return new TransferId(UUID.fromString(v)); }

    @Override
    public String toString() { return value.toString(); }
}
