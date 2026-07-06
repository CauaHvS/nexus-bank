package com.nexusbank.payments.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @Test
    void criacao_comValorValido_deveInstanciar() {
        IdempotencyKey key = new IdempotencyKey("chave-valida-123");

        assertThat(key.value()).isEqualTo("chave-valida-123");
        assertThat(key.toString()).isEqualTo("chave-valida-123");
    }

    @Test
    void criacao_comNull_deveLancarException() {
        assertThatThrownBy(() -> new IdempotencyKey(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    void criacao_comStringVazia_deveLancarException() {
        assertThatThrownBy(() -> new IdempotencyKey(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    void criacao_comStringApenasBrancos_deveLancarException() {
        assertThatThrownBy(() -> new IdempotencyKey("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    void criacao_comValorAcima64Chars_deveLancarException() {
        String valor65Chars = "a".repeat(65);

        assertThatThrownBy(() -> new IdempotencyKey(valor65Chars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }

    @Test
    void criacao_comExatamente64Chars_deveInstanciar() {
        String valor64Chars = "a".repeat(64);

        IdempotencyKey key = new IdempotencyKey(valor64Chars);

        assertThat(key.value()).hasSize(64);
    }

    @Test
    void equals_comMesmoValor_deveRetornarTrue() {
        IdempotencyKey k1 = new IdempotencyKey("chave-igual");
        IdempotencyKey k2 = new IdempotencyKey("chave-igual");

        assertThat(k1).isEqualTo(k2);
        assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
    }

    @Test
    void equals_comValoresDiferentes_deveRetornarFalse() {
        IdempotencyKey k1 = new IdempotencyKey("chave-a");
        IdempotencyKey k2 = new IdempotencyKey("chave-b");

        assertThat(k1).isNotEqualTo(k2);
    }
}
