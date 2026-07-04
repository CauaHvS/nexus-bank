package com.nexusbank.corebanking.domain.model;

import java.util.UUID;

public record CustomerId(UUID value) {

    public CustomerId {
        if (value == null) throw new IllegalArgumentException("CustomerId não pode ser nulo");
    }

    public static CustomerId of(String value) {
        return new CustomerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
