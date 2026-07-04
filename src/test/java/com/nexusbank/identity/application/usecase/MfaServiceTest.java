package com.nexusbank.identity.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaServiceTest {

    MfaService mfaService;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService();
    }

    @Test
    @DisplayName("generateSecret retorna string nao nula e nao vazia")
    void generateSecret_returnsNonNullString() {
        String secret = mfaService.generateSecret();

        assertThat(secret).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("duas chamadas a generateSecret retornam secrets distintos")
    void generateSecret_calledTwice_returnsDifferentSecrets() {
        String first = mfaService.generateSecret();
        String second = mfaService.generateSecret();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("codigo invalido 000000 retorna false para qualquer secret valido")
    void verifyCode_withInvalidCode_returnsFalse() {
        String secret = mfaService.generateSecret();

        // "000000" e estatisticamente impossivel de ser o TOTP correto no momento do teste
        boolean result = mfaService.verifyCode(secret, "000000");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("generateOtpAuthUri contem o email e o secret no URI")
    void generateOtpAuthUri_containsExpectedParts() {
        String secret = mfaService.generateSecret();
        String email = "usuario@nexusbank.com";
        String issuer = "NexusBank";

        String uri = mfaService.generateOtpAuthUri(secret, email, issuer);

        assertThat(uri)
                .startsWith("otpauth://totp/")
                .contains(email)
                .contains(secret)
                .contains(issuer)
                .contains("secret=")
                .contains("issuer=");
    }
}
