package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.payments.application.dto.InitiateTransferCommand;
import com.nexusbank.payments.application.dto.TransferResult;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do fluxo orquestrado da Saga de transferência.
 *
 * Exercem o fluxo completo de compensação sem banco de dados, usando Mockito
 * para simular os repositórios e o CoreBankingApi. Os use cases são instanciados
 * manualmente para que compartilhem os mesmos mocks.
 */
@ExtendWith(MockitoExtension.class)
class SagaCompensationTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private CoreBankingApi coreBankingApi;

    private InitiateTransferUseCase initiateUseCase;
    private CompensateTransferUseCase compensateUseCase;

    private static final String SOURCE_ACCOUNT = "acc-origem-001";
    private static final String TARGET_ACCOUNT = "acc-destino-002";
    private static final String USER_ID = "usuario-dono-001";
    private static final BigDecimal VALOR = new BigDecimal("100.00");
    private static final Money AMOUNT = Money.of(VALOR, Currency.BRL);

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        initiateUseCase = new InitiateTransferUseCase(
                transferRepository, outboxRepository, coreBankingApi, objectMapper);
        compensateUseCase = new CompensateTransferUseCase(
                transferRepository, outboxRepository, coreBankingApi, objectMapper);
    }

    private InitiateTransferCommand buildCommand(String idempotencyKey) {
        return new InitiateTransferCommand(
                SOURCE_ACCOUNT, TARGET_ACCOUNT,
                VALOR, "BRL",
                "PIX", idempotencyKey,
                "Transferencia de teste",
                USER_ID
        );
    }

    /**
     * Fluxo: initiate (debita, PENDING) -> compensate (credita estorno, FAILED).
     *
     * Prova que:
     * - debit chamado 1x na iniciacao
     * - credit chamado 1x no estorno
     * - save chamado 2x (1x PENDING + 1x FAILED)
     * - outbox chamado 2x (TransferInitiated + TransferFailed)
     * - ultimo Transfer salvo tem status FAILED
     */
    @Test
    void saga_creditoFalha_deveEstornarDebitoESalvarFailed() {
        // --- Arrange ---

        // idempotency key nao existe ainda
        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());

        // usuario e dono da conta de origem
        when(coreBankingApi.isOwner(SOURCE_ACCOUNT, USER_ID)).thenReturn(true);

        // captura o Transfer salvo pelo InitiateTransferUseCase
        ArgumentCaptor<Transfer> saveCaptor = ArgumentCaptor.forClass(Transfer.class);
        when(transferRepository.save(saveCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        // --- Act: fase 1 - initiacao ---
        TransferResult result = initiateUseCase.execute(buildCommand("saga-comp-001"));

        assertThat(result.status()).isEqualTo(TransferStatus.PENDING);

        // reconstitue o Transfer PENDING como o repositorio faria ao ler do banco
        // (sem eventos de dominio pendentes, pois o pullDomainEvents ja limpou)
        Transfer transferPendingSalvo = saveCaptor.getValue();
        Transfer transferReconstituted = Transfer.reconstitute(
                transferPendingSalvo.getId(),
                transferPendingSalvo.getSourceAccountId(),
                transferPendingSalvo.getTargetAccountId(),
                transferPendingSalvo.getAmount(),
                transferPendingSalvo.getIdempotencyKey(),
                transferPendingSalvo.getType(),
                TransferStatus.PENDING,
                null,
                transferPendingSalvo.getCreatedAt(),
                null,
                null
        );

        // o findById agora devolve o Transfer PENDING reconstituido
        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.of(transferReconstituted));

        // --- Act: fase 2 - compensacao (simula que o credito ao destino falhou) ---
        String transferId = result.transferId().toString();
        compensateUseCase.execute(transferId, "Falha no credito ao destino simulada");

        // --- Assert ---

        // debit chamado 1x (debito original na iniciacao)
        verify(coreBankingApi, times(1)).debit(anyString(), any(Money.class), anyString());

        // credit chamado 1x (estorno na conta de origem durante compensacao)
        verify(coreBankingApi, times(1)).credit(anyString(), any(Money.class), anyString());

        // save chamado 2x: 1x PENDING (initiacao) + 1x FAILED (compensacao)
        verify(transferRepository, times(2)).save(any(Transfer.class));

        // o segundo save deve ter status FAILED
        List<Transfer> todosSalvos = saveCaptor.getAllValues();
        assertThat(todosSalvos).hasSize(2);
        assertThat(todosSalvos.get(0).getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(todosSalvos.get(1).getStatus()).isEqualTo(TransferStatus.FAILED);

        // outbox chamado 2x: TransferInitiated (initiacao) + TransferFailed (compensacao)
        verify(outboxRepository, times(2)).save(anyString(), anyString(), anyString());
    }

    /**
     * Prova que nenhum centavo e perdido: saldo da origem volta ao valor inicial
     * apos o fluxo completo de iniciacao + compensacao.
     *
     * Simula a variacao de saldo via AtomicInteger (em centavos):
     * - debit: subtrai o valor
     * - credit (estorno): soma o valor de volta
     */
    @Test
    void saga_creditoFalha_saldoOriginalRestaurado() {
        // saldo inicial: 1000 centavos (R$10,00)
        AtomicInteger saldoOrigem = new AtomicInteger(1000);

        // --- Arrange ---
        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());
        when(coreBankingApi.isOwner(SOURCE_ACCOUNT, USER_ID)).thenReturn(true);

        ArgumentCaptor<Transfer> saveCaptor = ArgumentCaptor.forClass(Transfer.class);
        when(transferRepository.save(saveCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        // debit diminui o saldo (valor em centavos)
        doAnswer(inv -> {
            Money moneyArg = inv.getArgument(1);
            int centavos = moneyArg.amount().multiply(new BigDecimal("100")).intValue();
            saldoOrigem.addAndGet(-centavos);
            return null;
        }).when(coreBankingApi).debit(anyString(), any(Money.class), anyString());

        // credit aumenta o saldo (estorno)
        doAnswer(inv -> {
            Money moneyArg = inv.getArgument(1);
            int centavos = moneyArg.amount().multiply(new BigDecimal("100")).intValue();
            saldoOrigem.addAndGet(centavos);
            return null;
        }).when(coreBankingApi).credit(anyString(), any(Money.class), anyString());

        // --- Act: fase 1 - initiacao (debita R$100,00 = 10000 centavos) ---
        TransferResult result = initiateUseCase.execute(buildCommand("saga-saldo-001"));

        // saldo deve ter caido apos o debito
        int saldoAposDebito = saldoOrigem.get();
        assertThat(saldoAposDebito).isLessThan(1000);

        // reconstitui para a compensacao
        Transfer transferPendingSalvo = saveCaptor.getValue();
        Transfer transferReconstituted = Transfer.reconstitute(
                transferPendingSalvo.getId(),
                transferPendingSalvo.getSourceAccountId(),
                transferPendingSalvo.getTargetAccountId(),
                transferPendingSalvo.getAmount(),
                transferPendingSalvo.getIdempotencyKey(),
                transferPendingSalvo.getType(),
                TransferStatus.PENDING,
                null,
                transferPendingSalvo.getCreatedAt(),
                null,
                null
        );

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.of(transferReconstituted));

        // --- Act: fase 2 - compensacao (credita o estorno de volta) ---
        compensateUseCase.execute(result.transferId().toString(), "Falha simulada no credito");

        // --- Assert: saldo restaurado ao valor original ---
        assertThat(saldoOrigem.get())
                .as("Saldo da conta de origem deve ser restaurado apos compensacao")
                .isEqualTo(1000);
    }

    /**
     * Cenario extremo: o estorno (credit de volta) tambem falha.
     *
     * Prova que:
     * - o CompensateTransferUseCase nao propaga a excecao (nao explode)
     * - o Transfer e salvo com status COMPENSATION_FAILED
     * - nenhum evento TransferFailed e publicado no Outbox
     *   (markCompensationFailed nao adiciona evento ao domainEvents)
     */
    @Test
    void saga_creditoFalhaNoEstorno_deveMarcarCompensationFailed() {
        // --- Arrange ---

        // Transfer PENDING ja existente (simula que a iniciacao ja ocorreu)
        Transfer transferPending = Transfer.reconstitute(
                TransferId.generate(),
                SOURCE_ACCOUNT, TARGET_ACCOUNT,
                AMOUNT,
                new IdempotencyKey("saga-comp-falha-estorno-001"),
                PaymentType.PIX,
                TransferStatus.PENDING,
                null,
                Instant.now().minusSeconds(10),
                null,
                null
        );

        when(transferRepository.findById(any(TransferId.class)))
                .thenReturn(Optional.of(transferPending));

        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // qualquer chamada a credit lanca excecao (tanto crédito ao destino quanto estorno)
        doThrow(new RuntimeException("Timeout no banco central - estorno falhou"))
                .when(coreBankingApi).credit(anyString(), any(Money.class), anyString());

        // --- Act ---
        String transferId = transferPending.getId().toString();
        // nao deve propagar excecao - o use case captura internamente
        compensateUseCase.execute(transferId, "Falha no credito ao destino");

        // --- Assert ---

        // Transfer salvo com COMPENSATION_FAILED
        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(captor.capture());
        Transfer transferSalvo = captor.getValue();
        assertThat(transferSalvo.getStatus())
                .as("Status deve ser COMPENSATION_FAILED quando o estorno tambem falha")
                .isEqualTo(TransferStatus.COMPENSATION_FAILED);
        assertThat(transferSalvo.getFailureReason())
                .as("Motivo deve indicar falha na compensacao")
                .contains("Falha na compensação");

        // outbox NAO deve ter sido chamado com evento de falha:
        // markCompensationFailed nao adiciona evento ao domainEvents,
        // portanto pullDomainEvents() retorna lista vazia e save do outbox nao e invocado
        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }
}
