// Value object que representa um endereço de e-mail validado.
// A validação ocorre na construção: qualquer e-mail que não satisfaça o regex
// lança InvalidEmailException, impedindo que um Email inválido exista no domínio.
// Imutável por definição de record. Sem dependência de framework.
package com.nexusbank.identity.domain.model;

import com.nexusbank.identity.domain.exception.InvalidEmailException;

public record Email(String value) {

    private static final String EMAIL_REGEX =
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    public Email {
        if (value == null || !value.matches(EMAIL_REGEX))
            throw new InvalidEmailException(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
