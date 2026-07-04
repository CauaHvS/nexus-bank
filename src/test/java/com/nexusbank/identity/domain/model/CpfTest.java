package com.nexusbank.identity.domain.model;

import com.nexusbank.identity.domain.exception.InvalidCpfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cpf")
class CpfTest {

    // CPF matematicamente válido usado nos testes
    private static final String CPF_FORMATTED   = "529.982.247-25";
    private static final String CPF_UNFORMATTED = "52998224725";

    @Test
    @DisplayName("CPF válido formatado deve ser aceito")
    void cpf_withFormattedValidCpf_createsSuccessfully() {
        Cpf cpf = new Cpf(CPF_FORMATTED);

        assertThat(cpf.value()).isEqualTo(CPF_UNFORMATTED);
    }

    @Test
    @DisplayName("CPF válido sem formatação deve ser aceito")
    void cpf_withUnformattedValidCpf_createsSuccessfully() {
        Cpf cpf = new Cpf(CPF_UNFORMATTED);

        assertThat(cpf.value()).isEqualTo(CPF_UNFORMATTED);
    }

    @Test
    @DisplayName("CPF nulo deve lançar InvalidCpfException")
    void cpf_withNullValue_throwsInvalidCpfException() {
        assertThatThrownBy(() -> new Cpf(null))
                .isInstanceOf(InvalidCpfException.class);
    }

    @ParameterizedTest(name = "dígitos iguais: \"{0}\"")
    @DisplayName("CPF com todos os dígitos iguais deve lançar InvalidCpfException")
    @ValueSource(strings = {
            "111.111.111-11",
            "00000000000",
            "99999999999"
    })
    void cpf_withAllSameDigits_throwsInvalidCpfException(String value) {
        assertThatThrownBy(() -> new Cpf(value))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    @DisplayName("CPF com dígito verificador errado deve lançar InvalidCpfException")
    void cpf_withWrongVerifierDigit_throwsInvalidCpfException() {
        // CPF base correto 529.982.247-25; trocando o último dígito para 6
        assertThatThrownBy(() -> new Cpf("529.982.247-26"))
                .isInstanceOf(InvalidCpfException.class);
    }

    @ParameterizedTest(name = "comprimento errado: \"{0}\"")
    @DisplayName("CPF com comprimento errado deve lançar InvalidCpfException")
    @ValueSource(strings = {
            "5299822472",    // 10 dígitos
            "529982247250"   // 12 dígitos
    })
    void cpf_withWrongLength_throwsInvalidCpfException(String value) {
        assertThatThrownBy(() -> new Cpf(value))
                .isInstanceOf(InvalidCpfException.class);
    }

    @Test
    @DisplayName("formatted() deve retornar o CPF no formato ###.###.###-##")
    void formatted_returnsFormattedCpf() {
        Cpf cpf = new Cpf(CPF_UNFORMATTED);

        assertThat(cpf.formatted()).isEqualTo(CPF_FORMATTED);
    }

    @Test
    @DisplayName("CPF deve armazenar internamente apenas os dígitos, sem pontuação")
    void cpf_storesOnlyDigitsInternally() {
        Cpf fromFormatted   = new Cpf(CPF_FORMATTED);
        Cpf fromUnformatted = new Cpf(CPF_UNFORMATTED);

        assertThat(fromFormatted.value()).doesNotContain(".", "-");
        assertThat(fromFormatted.value()).isEqualTo(fromUnformatted.value());
    }
}
