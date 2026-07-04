package com.nexusbank.identity.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserId")
class UserIdTest {

    @Test
    @DisplayName("generate() deve retornar um UserId não nulo com UUID válido")
    void generate_calledOnce_returnsNonNullUserIdWithValidUuid() {
        UserId id = UserId.generate();

        assertThat(id).isNotNull();
        assertThat(id.value()).isNotNull();
        // UUID.fromString não lança se o toString for válido
        assertThat(id.toString()).isEqualTo(id.value().toString());
    }

    @Test
    @DisplayName("of(string válida) deve criar UserId equivalente ao UUID original")
    void of_withValidUuidString_createsUserIdCorrectly() {
        String raw = "550e8400-e29b-41d4-a716-446655440000";

        UserId id = UserId.of(raw);

        assertThat(id.value()).isEqualTo(UUID.fromString(raw));
        assertThat(id.toString()).isEqualTo(raw);
    }

    @Test
    @DisplayName("of(string inválida) deve lançar IllegalArgumentException")
    void of_withInvalidString_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> UserId.of("nao-e-um-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(string vazia) deve lançar IllegalArgumentException")
    void of_withEmptyString_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> UserId.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generate() chamado duas vezes deve produzir ids distintos")
    void generate_calledTwice_returnsDifferentIds() {
        UserId first = UserId.generate();
        UserId second = UserId.generate();

        assertThat(first).isNotEqualTo(second);
        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    @DisplayName("construtor com null deve lançar IllegalArgumentException")
    void constructor_withNullValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new UserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nulo");
    }
}
