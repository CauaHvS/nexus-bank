package com.nexusbank.payments.domain.model;

import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.payments.domain.event.TransferCompleted;
import com.nexusbank.payments.domain.event.TransferFailed;
import com.nexusbank.payments.domain.event.TransferInitiated;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    private static final String SOURCE = "acc-origem-001";
    private static final String TARGET = "acc-destino-002";
    private static final Money AMOUNT = Money.of(new BigDecimal("250.00"), Currency.BRL);
    private static final IdempotencyKey KEY = new IdempotencyKey("chave-idempotente-001");

    // --- Transfer.initiate ---

    @Test
    void initiate_deveRetornarTransferComStatusPending() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.getSourceAccountId()).isEqualTo(SOURCE);
        assertThat(transfer.getTargetAccountId()).isEqualTo(TARGET);
        assertThat(transfer.getAmount()).isEqualTo(AMOUNT);
        assertThat(transfer.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(transfer.getType()).isEqualTo(PaymentType.INTERNAL);
        assertThat(transfer.getId()).isNotNull();
        assertThat(transfer.getCreatedAt()).isNotNull();
    }

    @Test
    void initiate_deveEmitirEventoTransferInitiated() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.PIX);

        List<Object> events = transfer.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TransferInitiated.class);

        TransferInitiated event = (TransferInitiated) events.get(0);
        assertThat(event.transferId()).isEqualTo(transfer.getId());
        assertThat(event.sourceAccountId()).isEqualTo(SOURCE);
        assertThat(event.targetAccountId()).isEqualTo(TARGET);
        assertThat(event.amount()).isEqualTo(AMOUNT);
        assertThat(event.idempotencyKey()).isEqualTo(KEY);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void initiate_pullDomainEvents_deveLimparListaAposConsumo() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.TED);

        transfer.pullDomainEvents();
        List<Object> segundaLeitura = transfer.pullDomainEvents();

        assertThat(segundaLeitura).isEmpty();
    }

    // --- Transfer.complete ---

    @Test
    void complete_deveAlterarStatusParaCompleted() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL);
        transfer.pullDomainEvents(); // limpa evento de initiate

        transfer.complete();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.isCompleted()).isTrue();
        assertThat(transfer.getCompletedAt()).isNotNull();
    }

    @Test
    void complete_deveEmitirEventoTransferCompleted() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL);
        transfer.pullDomainEvents();

        transfer.complete();
        List<Object> events = transfer.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TransferCompleted.class);

        TransferCompleted event = (TransferCompleted) events.get(0);
        assertThat(event.transferId()).isEqualTo(transfer.getId());
        assertThat(event.sourceAccountId()).isEqualTo(SOURCE);
        assertThat(event.targetAccountId()).isEqualTo(TARGET);
        assertThat(event.amount()).isEqualTo(AMOUNT);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void complete_emTransferNaoPending_deveLancarIllegalStateException() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL);
        transfer.complete(); // primeira chamada: ok

        assertThatThrownBy(transfer::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    // --- Transfer.fail ---

    @Test
    void fail_deveAlterarStatusParaFailed() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.PIX);
        transfer.pullDomainEvents();

        transfer.fail("Saldo insuficiente");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.isPending()).isFalse();
        assertThat(transfer.getFailureReason()).isEqualTo("Saldo insuficiente");
    }

    @Test
    void fail_deveEmitirEventoTransferFailed() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.TED);
        transfer.pullDomainEvents();

        transfer.fail("Conta destino inativa");
        List<Object> events = transfer.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TransferFailed.class);

        TransferFailed event = (TransferFailed) events.get(0);
        assertThat(event.transferId()).isEqualTo(transfer.getId());
        assertThat(event.sourceAccountId()).isEqualTo(SOURCE);
        assertThat(event.reason()).isEqualTo("Conta destino inativa");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void fail_emTransferNaoPending_deveLancarIllegalStateException() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL);
        transfer.fail("primeiro erro");

        assertThatThrownBy(() -> transfer.fail("segundo erro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    // --- Transfer.markCompensationFailed ---

    @Test
    void markCompensationFailed_deveAlterarStatusParaCompensationFailed() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL);
        transfer.pullDomainEvents();

        transfer.markCompensationFailed("Falha no estorno: timeout");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPENSATION_FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("Falha no estorno: timeout");
    }

    @Test
    void markCompensationFailed_naoDeveEmitirEventoDeDominio() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL);
        transfer.pullDomainEvents();

        transfer.markCompensationFailed("timeout no banco externo");
        List<Object> events = transfer.pullDomainEvents();

        assertThat(events).isEmpty();
    }

    // --- Transfer.reconstitute ---

    @Test
    void reconstitute_deveRestaurarEstadoSemEmitirEventos() {
        TransferId id = TransferId.generate();
        Transfer transfer = Transfer.reconstitute(
                id, SOURCE, TARGET, AMOUNT, KEY, PaymentType.PIX,
                TransferStatus.COMPLETED, null,
                java.time.Instant.now().minusSeconds(60),
                java.time.Instant.now(), null);

        assertThat(transfer.getId()).isEqualTo(id);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.isCompleted()).isTrue();
        assertThat(transfer.pullDomainEvents()).isEmpty();
    }

    // --- Transfer.initiate com scheduledFor ---

    @Test
    void initiate_comScheduledFor_deveRetornarStatusScheduled() {
        java.time.Instant futuro = java.time.Instant.now().plusSeconds(3600);

        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL, futuro);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.SCHEDULED);
        assertThat(transfer.isScheduled()).isTrue();
        assertThat(transfer.getScheduledFor()).isEqualTo(futuro);
    }

    @Test
    void initiate_semScheduledFor_deveRetornarStatusPending() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL, null);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.isPending()).isTrue();
    }

    // --- Transfer.activate ---

    @Test
    void activate_deveTransicionarDeScheduledParaPending() {
        java.time.Instant futuro = java.time.Instant.now().plusSeconds(3600);
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL, futuro);
        transfer.pullDomainEvents(); // SCHEDULED nao emite evento, mas limpa por garantia

        transfer.activate();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.isPending()).isTrue();
        // activate deve emitir TransferInitiated
        List<Object> events = transfer.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TransferInitiated.class);
    }

    @Test
    void activate_emTransferNaoScheduled_deveLancarIllegalStateException() {
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL);
        // status e PENDING

        assertThatThrownBy(transfer::activate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCHEDULED");
    }

    // --- Transfer.fail em SCHEDULED ---

    @Test
    void fail_emTransferScheduled_devePermitirCancelamento() {
        java.time.Instant futuro = java.time.Instant.now().plusSeconds(3600);
        Transfer transfer = Transfer.initiate(SOURCE, TARGET, AMOUNT, KEY, PaymentType.INTERNAL, futuro);

        transfer.fail("Cancelado pelo usuário");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("Cancelado pelo usuário");
    }
}
