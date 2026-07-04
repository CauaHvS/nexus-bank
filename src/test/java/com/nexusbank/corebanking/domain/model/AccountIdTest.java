package com.nexusbank.corebanking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountId")
class AccountIdTest {

    @Test
    @DisplayName("generate() retorna IDs distintos a cada chamada")
    void generate_returnsDifferentIds() {
        AccountId id1 = AccountId.generate();
        AccountId id2 = AccountId.generate();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    @DisplayName("of() com UUID string válida parseia corretamente")
    void of_withValidString_parsesUUID() {
        String raw = UUID.randomUUID().toString();

        AccountId id = AccountId.of(raw);

        assertThat(id.value().toString()).isEqualTo(raw);
        assertThat(id.toString()).isEqualTo(raw);
    }

    @Test
    @DisplayName("of() com string inválida lança IllegalArgumentException")
    void of_withInvalidString_throwsException() {
        assertThatThrownBy(() -> AccountId.of("nao-e-um-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("construtor com null lança IllegalArgumentException")
    void constructor_withNull_throwsException() {
        assertThatThrownBy(() -> new AccountId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nulo");
    }
}
