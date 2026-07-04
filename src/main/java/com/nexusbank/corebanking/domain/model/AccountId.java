package com.nexusbank.corebanking.domain.model;

import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        if (value == null) throw new IllegalArgumentException("AccountId não pode ser nulo");
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
