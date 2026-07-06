package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.payments.application.dto.InitiateTransferCommand;
import com.nexusbank.payments.application.dto.TransferResult;
import com.nexusbank.payments.domain.exception.AccountAccessDeniedException;
import com.nexusbank.payments.domain.exception.DuplicateTransferException;
import com.nexusbank.payments.domain.model.IdempotencyKey;
import com.nexusbank.payments.domain.model.PaymentType;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.model.TransferStatus;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class InitiateTransferUseCaseTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private CoreBankingApi coreBankingApi;

    private InitiateTransferUseCase useCase;

    private static final String SOURCE = "acc-origem-001";
    private static final String TARGET = "acc-destino-002";
    private static final String IDEM_KEY = "chave-idempotente-001";
    private static final String USER_ID = "usuario-dono-001";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        useCase = new InitiateTransferUseCase(transferRepository, outboxRepository, coreBankingApi, objectMapper);
    }

    private InitiateTransferCommand buildCommand(String idempotencyKey) {
        return new InitiateTransferCommand(
                SOURCE, TARGET,
                new BigDecimal("500.00"), "BRL",
                "INTERNAL", idempotencyKey,
                "Pagamento de serviço",
                USER_ID
        );
    }

    @Test
    void execute_cenarioHappy_deveDebitarSalvarEPublicarOutbox() {
        InitiateTransferCommand command = buildCommand(IDEM_KEY);

        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());
        when(coreBankingApi.isOwner(SOURCE, USER_ID)).thenReturn(true);

        // save devolve o mesmo objeto que recebeu
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(result.sourceAccountId()).isEqualTo(SOURCE);
        assertThat(result.targetAccountId()).isEqualTo(TARGET);
        assertThat(result.idempotencyKey()).isEqualTo(IDEM_KEY);

        verify(transferRepository).save(any(Transfer.class));
        verify(coreBankingApi).debit(anyString(), any(Money.class), anyString());
        verify(outboxRepository).save(anyString(), anyString(), anyString());
    }

    @Test
    void execute_usuarioNaoDonoDaConta_deveLancarAccountAccessDeniedException() {
        InitiateTransferCommand command = buildCommand(IDEM_KEY);

        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());
        when(coreBankingApi.isOwner(SOURCE, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(AccountAccessDeniedException.class)
                .hasMessageContaining(SOURCE);

        verify(coreBankingApi, never()).debit(anyString(), any(Money.class), anyString());
        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void execute_idempotencyKeyDuplicadaComTransferenciaPending_deveLancarDuplicateTransferException() {
        InitiateTransferCommand command = buildCommand(IDEM_KEY);

        Transfer existente = Transfer.initiate(SOURCE, TARGET,
                Money.of(new BigDecimal("500.00"), Currency.BRL),
                new IdempotencyKey(IDEM_KEY), PaymentType.INTERNAL);

        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DuplicateTransferException.class)
                .hasMessageContaining(IDEM_KEY);

        verify(coreBankingApi, never()).debit(anyString(), any(Money.class), anyString());
        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void execute_idempotencyKeyDuplicadaComTransferenciaCompleted_deveRetornarResultadoExistente() {
        InitiateTransferCommand command = buildCommand(IDEM_KEY);

        Transfer existente = Transfer.initiate(SOURCE, TARGET,
                Money.of(new BigDecimal("500.00"), Currency.BRL),
                new IdempotencyKey(IDEM_KEY), PaymentType.INTERNAL);
        existente.complete(); // simula transferencia ja completada

        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.of(existente));

        TransferResult result = useCase.execute(command);

        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        verify(coreBankingApi, never()).debit(anyString(), any(Money.class), anyString());
    }

    @Test
    void execute_debitoFalhaComException_naoDevePublicarOutbox() {
        InitiateTransferCommand command = buildCommand(IDEM_KEY);

        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());
        when(coreBankingApi.isOwner(SOURCE, USER_ID)).thenReturn(true);
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // simula falha de saldo insuficiente — debit é void, então usa doThrow
        doThrow(new RuntimeException("Saldo insuficiente"))
                .when(coreBankingApi).debit(anyString(), any(Money.class), anyString());

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(RuntimeException.class);

        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }
}
