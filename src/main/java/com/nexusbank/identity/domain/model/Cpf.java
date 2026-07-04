// Value object que representa um CPF validado e normalizado.
// Valida CPF pelo algoritmo dos dígitos verificadores.
// Armazena apenas os 11 dígitos numéricos (sem pontos ou traço).
// Rejeita sequências com todos os dígitos iguais (ex: 111.111.111-11).
// Imutável por definição de record. Sem dependência de framework.
package com.nexusbank.identity.domain.model;

import com.nexusbank.identity.domain.exception.InvalidCpfException;

public record Cpf(String value) {

    public Cpf {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (!isValid(digits))
            throw new InvalidCpfException(value);
        value = digits;
    }

    public static boolean isValid(String digits) {
        if (digits == null || digits.length() != 11) return false;
        if (digits.chars().distinct().count() == 1) return false;

        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (digits.charAt(i) - '0') * (10 - i);
        int first = 11 - (sum % 11);
        if (first >= 10) first = 0;
        if (first != (digits.charAt(9) - '0')) return false;

        sum = 0;
        for (int i = 0; i < 10; i++) sum += (digits.charAt(i) - '0') * (11 - i);
        int second = 11 - (sum % 11);
        if (second >= 10) second = 0;
        return second == (digits.charAt(10) - '0');
    }

    public String formatted() {
        return value.substring(0, 3) + "."
                + value.substring(3, 6) + "."
                + value.substring(6, 9) + "-"
                + value.substring(9);
    }
}
