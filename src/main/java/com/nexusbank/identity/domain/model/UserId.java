// Value object que envolve UUID para identificar unicamente um User.
// Imutável por definição de record. Garante que um UserId nunca seja nulo.
// Não depende de nenhum framework — puro Java.
package com.nexusbank.identity.domain.model;

import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        if (value == null) throw new IllegalArgumentException("UserId não pode ser nulo");
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
