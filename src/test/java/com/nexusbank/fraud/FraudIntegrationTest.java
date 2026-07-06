package com.nexusbank.fraud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.payments.domain.port.out.TransferMetricsPort;
import com.nexusbank.payments.application.dto.InitiateTransferCommand;
import com.nexusbank.payments.application.dto.TransferResult;
import com.nexusbank.payments.application.usecase.InitiateTransferUseCase;
import com.nexusbank.payments.domain.model.IdempotencyKey;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.model.PaymentType;
import com.nexusbank.payments.domain.model.TransferStatus;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa o fluxo Fraud <-> Payments via mocks, sem banco ou Spring context.
 * Prova que InitiateTransferUseCase reage corretamente a cada FraudDecision.
 */
@ExtendWith(MockitoExtension.class)
class FraudIntegrationTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private CoreBankingApi coreBankingApi;

    @Mock
    private FraudApi fraudApi;

    @Mock
    private TransferMetricsPort metrics;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    private InitiateTransferUseCase useCase;

    private static final String SOURCE = "acc-origem-001";
    private static final String TARGET = "acc-destino-001";
    private static final String USER_ID = "user-001";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        org.mockito.Mockito.lenient().when(tracer.nextSpan()).thenReturn(span);
        org.mockito.Mockito.lenient().when(span.name(org.mockito.ArgumentMatchers.any())).thenReturn(span);
        org.mockito.Mockito.lenient().when(span.start()).thenReturn(span);
        org.mockito.Mockito.lenient().when(span.tag(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(span);
        org.mockito.Mockito.lenient().when(tracer.withSpan(org.mockito.ArgumentMatchers.any())).thenReturn(() -> {});
        useCase = new InitiateTransferUseCase(
                transferRepository, outboxRepository, coreBankingApi, fraudApi, objectMapper, metrics, tracer);
    }

    private InitiateTransferCommand comando(String idemKey, BigDecimal amount) {
        return new InitiateTransferCommand(
                SOURCE, TARGET, amount, "BRL", "INTERNAL", idemKey,
                "Pagamento de servico", USER_ID);
    }

    @Test
    void initiate_comTransferenciaSuspeita_deveRetornarUnderReview() {
        // FraudApi retorna SUSPICIOUS — transfer deve ser marcada UNDER_REVIEW sem debitar
        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());
        when(coreBankingApi.isOwner(SOURCE, USER_ID)).thenReturn(true);
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fraudApi.evaluate(any(FraudEvaluationRequest.class)))
                .thenReturn(FraudDecision.SUSPICIOUS);

        TransferResult resultado = useCase.execute(comando("key-suspicious-001", new BigDecimal("6000.00")));

        assertThat(resultado.status()).isEqualTo(TransferStatus.UNDER_REVIEW);

        // Debito nao pode ocorrer em transferencia suspeita
        verify(coreBankingApi, never()).debit(anyString(), any(Money.class), anyString());
        // Outbox nao deve ser publicado — transfer fica aguardando revisao
        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void initiate_comTransferenciaBloqueada_deveLancarFraudBlockedException() {
        // FraudApi lanca FraudBlockedException antes de retornar — simula decisao BLOCKED
        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());
        when(coreBankingApi.isOwner(SOURCE, USER_ID)).thenReturn(true);
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fraudApi.evaluate(any(FraudEvaluationRequest.class)))
                .thenThrow(new FraudBlockedException("transfer-bloqueada-001", 100));

        assertThatThrownBy(() -> useCase.execute(comando("key-blocked-001", new BigDecimal("6000.00"))))
                .isInstanceOf(FraudBlockedException.class);

        // Debito nunca ocorre quando transfer eh bloqueada
        verify(coreBankingApi, never()).debit(anyString(), any(Money.class), anyString());
        // Outbox nunca eh publicado
        verify(outboxRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void initiate_comTransferenciaAprovada_deveDebitarNormalmente() {
        // FraudApi retorna APPROVED — fluxo normal: debito + outbox
        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());
        when(coreBankingApi.isOwner(SOURCE, USER_ID)).thenReturn(true);
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fraudApi.evaluate(any(FraudEvaluationRequest.class)))
                .thenReturn(FraudDecision.APPROVED);

        TransferResult resultado = useCase.execute(comando("key-approved-001", new BigDecimal("500.00")));

        assertThat(resultado.status()).isEqualTo(TransferStatus.PENDING);

        verify(coreBankingApi).debit(anyString(), any(Money.class), anyString());
        verify(outboxRepository).save(anyString(), anyString(), anyString());
    }
}
