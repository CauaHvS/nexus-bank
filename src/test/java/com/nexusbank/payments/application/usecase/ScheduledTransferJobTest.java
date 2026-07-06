package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledTransferJobTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private CoreBankingApi coreBankingApi;

    private ScheduledTransferJob job;

    private static final Money AMOUNT = Money.of(new BigDecimal("300.00"), Currency.BRL);
    private static final String SOURCE = "acc-origem-001";
    private static final String TARGET = "acc-destino-002";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        job = new ScheduledTransferJob(transferRepository, outboxRepository, coreBankingApi, objectMapper);
    }

    private Transfer transferScheduledReconstituted(String idempotencyKeySuffix) {
        return Transfer.reconstitute(
                TransferId.generate(),
                SOURCE, TARGET, AMOUNT,
                new IdempotencyKey("key-" + idempotencyKeySuffix),
                PaymentType.INTERNAL,
                TransferStatus.SCHEDULED,
                null,
                Instant.now().minusSeconds(7200),
                null,
                Instant.now().minusSeconds(60)
        );
    }

    @Test
    void processScheduledTransfers_comTransferenciasVencidas_deveAtivarEDebitar() {
        Transfer transfer = transferScheduledReconstituted("001");
        when(transferRepository.findDueScheduled(any(Instant.class)))
                .thenReturn(List.of(transfer));
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        job.processScheduledTransfers();

        verify(coreBankingApi).debit(anyString(), any(Money.class), anyString());
        verify(transferRepository).save(any(Transfer.class));
        verify(outboxRepository).save(anyString(), anyString(), anyString());
    }

    @Test
    void processScheduledTransfers_semTransferencias_naoFazNada() {
        when(transferRepository.findDueScheduled(any(Instant.class)))
                .thenReturn(List.of());

        job.processScheduledTransfers();

        verify(coreBankingApi, never()).debit(anyString(), any(Money.class), anyString());
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void processScheduledTransfers_falhaNoDebito_deveContinuarProcessandoOutras() {
        Transfer primeiraTransfer = transferScheduledReconstituted("001");
        Transfer segundaTransfer = transferScheduledReconstituted("002");

        when(transferRepository.findDueScheduled(any(Instant.class)))
                .thenReturn(List.of(primeiraTransfer, segundaTransfer));
        // save sempre retorna o argumento recebido
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // primeira transferencia falha no debito
        doThrow(new RuntimeException("Saldo insuficiente"))
                .doNothing()
                .when(coreBankingApi).debit(anyString(), any(Money.class), anyString());

        // nao deve lancar excecao — o job captura internamente por transfer
        job.processScheduledTransfers();

        // save e chamado 2x: uma vez para cada transferencia (antes do debit)
        verify(transferRepository, times(2)).save(any(Transfer.class));
        // outbox apenas para a segunda (a primeira falhou no debit antes de chegar no outbox)
        verify(outboxRepository, times(1)).save(anyString(), anyString(), anyString());
    }
}
