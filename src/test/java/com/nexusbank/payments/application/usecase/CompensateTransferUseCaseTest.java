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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensateTransferUseCaseTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private CoreBankingApi coreBankingApi;

    @Mock
    private TransferMetricsPort metrics;

    private CompensateTransferUseCase useCase;

    private static final Money AMOUNT = Money.of(new BigDecimal("150.00"), Currency.BRL);
    private static final String MOTIVO = "Fraude detectada";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        useCase = new CompensateTransferUseCase(transferRepository, outboxRepository, coreBankingApi, objectMapper, metrics);
    }

    private Transfer criarTransferenciaPending(String idemKey) {
        // reconstitue simula o que o repositório faz ao ler do banco:
        // aggregate sem eventos de domínio pendentes
        return Transfer.reconstitute(
                TransferId.generate(),
                "acc-origem-001", "acc-destino-002",
                AMOUNT, new IdempotencyKey(idemKey),
                PaymentType.PIX, TransferStatus.PENDING,
                null, java.time.Instant.now().minusSeconds(5), null, null);
    }

    @Test
    void execute_cenarioHappy_deveCreditarEstornoEFalharTransferencia() {
        Transfer transfer = criarTransferenciaPending("chave-compensate-001");
        String transferId = transfer.getId().toString();

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(transferId, MOTIVO);

        // verifica que creditou o estorno na conta de origem
        verify(coreBankingApi).credit(anyString(), any(Money.class), anyString());

        // verifica que salva com status FAILED
        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).isEqualTo(MOTIVO);

        // verifica que publicou evento TransferFailed no outbox
        verify(outboxRepository).save(anyString(), anyString(), anyString());
    }

    @Test
    void execute_falhaNoEstorno_deveSalvarStatusCompensationFailed() {
        Transfer transfer = criarTransferenciaPending("chave-compensate-002");
        String transferId = transfer.getId().toString();

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // simula falha no crédito de estorno
        doThrow(new RuntimeException("Timeout no banco central"))
                .when(coreBankingApi).credit(anyString(), any(Money.class), anyString());

        useCase.execute(transferId, MOTIVO);

        // verifica que salva com status COMPENSATION_FAILED (não propaga exceção)
        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransferStatus.COMPENSATION_FAILED);
        assertThat(captor.getValue().getFailureReason()).contains("Falha na compensação");
    }

    @Test
    void execute_transferenciaInexistente_deveLancarTransferNotFoundException() {
        String idInexistente = TransferId.generate().toString();

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(idInexistente, MOTIVO))
                .isInstanceOf(TransferNotFoundException.class)
                .hasMessageContaining(idInexistente);

        verify(coreBankingApi, never()).credit(anyString(), any(Money.class), anyString());
    }

    @Test
    void execute_transferenciaJaFailed_naoDeveReprocessar() {
        // transferência que já está FAILED não é PENDING, não deve ser reprocessada
        Transfer transfer = criarTransferenciaPending("chave-compensate-003");
        transfer.fail("falha anterior"); // torna não-PENDING
        String transferId = transfer.getId().toString();

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.of(transfer));

        useCase.execute(transferId, MOTIVO);

        verify(coreBankingApi, never()).credit(anyString(), any(Money.class), anyString());
        verify(transferRepository, never()).save(any(Transfer.class));
    }
}
