package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FraudRulesTest {

    // --- Utilitario ---

    private FraudContext contexto(BigDecimal amount, int count, boolean novoDestino) {
        return new FraudContext(
                "transfer-001",
                "user-001",
                "acc-origem-001",
                "acc-destino-001",
                amount,
                "INTERNAL",
                count,
                novoDestino
        );
    }

    // ===================== HighValueRule =====================

    @Test
    void highValue_abaixoDe5000_deveRetornarZero() {
        HighValueRule rule = new HighValueRule();
        FraudContext ctx = contexto(new BigDecimal("4999.99"), 0, false);

        assertThat(rule.score(ctx)).isZero();
    }

    @Test
    void highValue_exatamente5000_deveRetornarZero() {
        // Limiar exclusivo: > 5000, nao >= 5000
        HighValueRule rule = new HighValueRule();
        FraudContext ctx = contexto(new BigDecimal("5000.00"), 0, false);

        assertThat(rule.score(ctx)).isZero();
    }

    @Test
    void highValue_acimaDe5000_deveRetornarScore40() {
        HighValueRule rule = new HighValueRule();
        FraudContext ctx = contexto(new BigDecimal("5000.01"), 0, false);

        assertThat(rule.score(ctx)).isEqualTo(40);
    }

    // ===================== HighFrequencyRule =====================

    @Test
    void highFrequency_abaixoDe5Transferencias_deveRetornarZero() {
        HighFrequencyRule rule = new HighFrequencyRule();
        FraudContext ctx = contexto(new BigDecimal("100.00"), 4, false);

        assertThat(rule.score(ctx)).isZero();
    }

    @Test
    void highFrequency_exatamente5Transferencias_deveRetornarScore35() {
        // Limiar inclusivo: >= 5
        HighFrequencyRule rule = new HighFrequencyRule();
        FraudContext ctx = contexto(new BigDecimal("100.00"), 5, false);

        assertThat(rule.score(ctx)).isEqualTo(35);
    }

    @Test
    void highFrequency_acimaDe5Transferencias_deveRetornarScore35() {
        HighFrequencyRule rule = new HighFrequencyRule();
        FraudContext ctx = contexto(new BigDecimal("100.00"), 10, false);

        assertThat(rule.score(ctx)).isEqualTo(35);
    }

    // ===================== NewDestinationRule =====================

    @Test
    void newDestination_destinoConhecido_deveRetornarZero() {
        NewDestinationRule rule = new NewDestinationRule();
        FraudContext ctx = contexto(new BigDecimal("100.00"), 0, false);

        assertThat(rule.score(ctx)).isZero();
    }

    @Test
    void newDestination_destinoNovo_deveRetornarScore25() {
        NewDestinationRule rule = new NewDestinationRule();
        FraudContext ctx = contexto(new BigDecimal("100.00"), 0, true);

        assertThat(rule.score(ctx)).isEqualTo(25);
    }

    // ===================== Verificacao de nomes das regras =====================

    @Test
    void highValueRule_name_deveRetornarNomeCorreto() {
        assertThat(new HighValueRule().name()).isEqualTo("HighValueRule");
    }

    @Test
    void highFrequencyRule_name_deveRetornarNomeCorreto() {
        assertThat(new HighFrequencyRule().name()).isEqualTo("HighFrequencyRule");
    }

    @Test
    void newDestinationRule_name_deveRetornarNomeCorreto() {
        assertThat(new NewDestinationRule().name()).isEqualTo("NewDestinationRule");
    }
}
