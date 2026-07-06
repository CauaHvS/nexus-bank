package com.nexusbank.payments.application.usecase;

import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.payments.domain.exception.TransferNotFoundException;
import com.nexusbank.payments.domain.model.IdempotencyKey;
import com.nexusbank.payments.domain.model.PaymentType;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.model.TransferId;
import com.nexusbank.payments.domain.model.TransferStatus;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelScheduledTransferUseCaseTest {

    @Mock
    private TransferRepository transferRepository;

    // OutboxRepository declarado para documentar que o use case NAO o usa
    // CancelScheduledTransferUseCase nao depende de OutboxRepository nem CoreBankingApi
    @Mock
    private OutboxRepository outboxRepository;

    private CancelScheduledTransferUseCase useCase;

    private static final String USER_ID = "usuario-001";
    private static final Money AMOUNT = Money.of(new BigDecimal("150.00"), Currency.BRL);
    private static final String SOURCE = "acc-origem-001";
    private static final String TARGET = "acc-destino-002";

    @BeforeEach
    void setUp() {
        useCase = new CancelScheduledTransferUseCase(transferRepository);
    }

    private Transfer transferScheduled(TransferId id) {
        return Transfer.reconstitute(
                id, SOURCE, TARGET, AMOUNT,
                new IdempotencyKey("key-cancel-001"),
                PaymentType.INTERNAL,
                TransferStatus.SCHEDULED,
                null,
                Instant.now().minusSeconds(3600),
                null,
                Instant.now().plusSeconds(3600)
        );
    }

    private Transfer transferPending(TransferId id) {
        return Transfer.reconstitute(
                id, SOURCE, TARGET, AMOUNT,
                new IdempotencyKey("key-cancel-002"),
                PaymentType.INTERNAL,
                TransferStatus.PENDING,
                null,
                Instant.now().minusSeconds(60),
                null,
                null
        );
    }

    @Test
    void execute_transferenciaScheduled_deveMarcarComoFalha() {
        TransferId id = TransferId.generate();
        Transfer transfer = transferScheduled(id);

        when(transferRepository.findById(id)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(id.toString(), USER_ID);

        verify(transferRepository).save(any(Transfer.class));
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("Cancelado pelo usuário");
        // cancelamento nao publica evento no outbox
        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void execute_transferenciaInexistente_deveLancarTransferNotFoundException() {
        TransferId id = TransferId.generate();

        when(transferRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id.toString(), USER_ID))
                .isInstanceOf(TransferNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(transferRepository, never()).save(any(Transfer.class));
    }

    @Test
    void execute_transferenciaNaoScheduled_deveLancarIllegalStateException() {
        TransferId id = TransferId.generate();
        Transfer transfer = transferPending(id);

        when(transferRepository.findById(id)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> useCase.execute(id.toString(), USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agendada");

        verify(transferRepository, never()).save(any(Transfer.class));
    }
}
