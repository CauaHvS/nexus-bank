package com.nexusbank.identity.application.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    @DisplayName("mesmo input sempre produz o mesmo hash (deterministico)")
    void hash_sameInput_returnsSameHash() {
        String token = "eyJhbGciOiJIUzI1NiJ9.test.token";

        String first = TokenHasher.hash(token);
        String second = TokenHasher.hash(token);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("inputs diferentes produzem hashes diferentes")
    void hash_differentInputs_returnsDifferentHashes() {
        String tokenA = "token-alpha";
        String tokenB = "token-beta";

        assertThat(TokenHasher.hash(tokenA)).isNotEqualTo(TokenHasher.hash(tokenB));
    }

    @Test
    @DisplayName("SHA-256 produz 32 bytes = 64 caracteres hexadecimais")
    void hash_returnsHexString64Chars() {
        String hash = TokenHasher.hash("qualquer-token-de-teste");

        assertThat(hash).hasSize(64);
        // Verifica que e hexadecimal valido (apenas 0-9 e a-f)
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("o hash nao contem o texto original do token")
    void hash_doesNotReturnPlainText() {
        String token = "meu-refresh-token-secreto";

        String hash = TokenHasher.hash(token);

        assertThat(hash).doesNotContain(token);
    }
}
