package com.nexusbank.identity.domain.model;

import com.nexusbank.identity.domain.exception.InvalidEmailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Email")
class EmailTest {

    @ParameterizedTest(name = "email válido: \"{0}\"")
    @DisplayName("emails válidos devem ser aceitos")
    @ValueSource(strings = {
            "user@example.com",
            "user.name+tag@domain.co.uk"
    })
    void email_withValidFormat_createsEmailSuccessfully(String address) {
        Email email = new Email(address);

        assertThat(email.value()).isEqualTo(address);
    }

    @Test
    @DisplayName("email nulo deve lançar InvalidEmailException")
    void email_withNullValue_throwsInvalidEmailException() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(InvalidEmailException.class);
    }

    @ParameterizedTest(name = "email inválido: \"{0}\"")
    @DisplayName("emails com formato inválido devem lançar InvalidEmailException")
    @ValueSource(strings = {
            "semdominio",
            "@sem-usuario.com",
            "sem-arroba"
    })
    void email_withInvalidFormat_throwsInvalidEmailException(String address) {
        assertThatThrownBy(() -> new Email(address))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    @DisplayName("toString() deve retornar o valor original do endereço")
    void toString_returnsOriginalEmailValue() {
        String address = "user@example.com";

        Email email = new Email(address);

        assertThat(email.toString()).isEqualTo(address);
    }
}
