package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.payments.domain.port.out.TransferMetricsPort;
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
import org.mockito.ArgumentCaptor;
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
class CompleteTransferUseCaseTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private CoreBankingApi coreBankingApi;

    @Mock
    private TransferMetricsPort metrics;

    private CompleteTransferUseCase useCase;

    private static final Money AMOUNT = Money.of(new BigDecimal("300.00"), Currency.BRL);

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        useCase = new CompleteTransferUseCase(transferRepository, outboxRepository, coreBankingApi, objectMapper, metrics);
    }

    private Transfer criarTransferenciaPending(String idemKey) {
        // reconstitue simula o que o repositório faz ao ler do banco:
        // aggregate sem eventos de domínio pendentes
        return Transfer.reconstitute(
                TransferId.generate(),
                "acc-origem-001", "acc-destino-002",
                AMOUNT, new IdempotencyKey(idemKey),
                PaymentType.INTERNAL, TransferStatus.PENDING,
                null, java.time.Instant.now().minusSeconds(5), null, null);
    }

    @Test
    void execute_cenarioHappy_deveCreditarECompletarTransferencia() {
        Transfer transfer = criarTransferenciaPending("chave-complete-001");
        String transferId = transfer.getId().toString();

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(transferId);

        // verifica que creditou na conta destino
        verify(coreBankingApi).credit(anyString(), any(Money.class), anyString());

        // captura o transfer salvo e verifica status
        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransferStatus.COMPLETED);

        // verifica que publicou evento no outbox
        verify(outboxRepository).save(anyString(), anyString(), anyString());
    }

    @Test
    void execute_transferenciaInexistente_deveLancarTransferNotFoundException() {
        String idInexistente = TransferId.generate().toString();

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(idInexistente))
                .isInstanceOf(TransferNotFoundException.class)
                .hasMessageContaining(idInexistente);

        verify(coreBankingApi, never()).credit(anyString(), any(Money.class), anyString());
        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void execute_transferenciaJaCompleted_naoDeveReprocessar() {
        // reconstitui uma transferência já COMPLETED (não PENDING)
        TransferId id = TransferId.generate();
        Transfer transferCompleted = Transfer.reconstitute(
                id, "acc-origem-001", "acc-destino-002",
                AMOUNT, new IdempotencyKey("chave-complete-002"),
                PaymentType.INTERNAL, TransferStatus.COMPLETED,
                null, Instant.now().minusSeconds(60), Instant.now(), null);

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.of(transferCompleted));

        useCase.execute(id.toString());

        verify(coreBankingApi, never()).credit(anyString(), any(Money.class), anyString());
        verify(transferRepository, never()).save(any(Transfer.class));
    }
}
