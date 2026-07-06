package com.nexusbank.fraud.application.usecase;

import com.nexusbank.fraud.FraudBlockedException;
import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.FraudEvaluationRequest;
import com.nexusbank.fraud.domain.model.FraudEvaluation;
import com.nexusbank.fraud.domain.port.out.FraudAuditRepository;
import com.nexusbank.fraud.domain.port.out.FraudContextRepository;
import com.nexusbank.fraud.domain.rule.HighFrequencyRule;
import com.nexusbank.fraud.domain.rule.HighValueRule;
import com.nexusbank.fraud.domain.rule.NewDestinationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluateFraudUseCaseTest {

    @Mock
    private FraudAuditRepository fraudAuditRepository;

    @Mock
    private FraudContextRepository fraudContextRepository;

    private EvaluateFraudUseCase useCase;

    private static final String TRANSFER_ID = "transfer-abc-001";
    private static final String USER_ID = "user-001";
    private static final String SOURCE = "acc-origem-001";
    private static final String TARGET = "acc-destino-001";

    @BeforeEach
    void setUp() {
        // Instancia com as regras reais — sem mock de regras
        useCase = new EvaluateFraudUseCase(
                List.of(new HighValueRule(), new HighFrequencyRule(), new NewDestinationRule()),
                fraudAuditRepository,
                fraudContextRepository
        );
    }

    private FraudEvaluationRequest request(BigDecimal amount) {
        return new FraudEvaluationRequest(TRANSFER_ID, USER_ID, SOURCE, TARGET, amount, "INTERNAL");
    }

    @Test
    void execute_transferenciaSegura_deveRetornarApproved() {
        // score = 0 (valor baixo + baixa frequencia + destino conhecido)
        when(fraudContextRepository.countTransfersLast24h(USER_ID)).thenReturn(1);
        when(fraudContextRepository.isNewDestination(USER_ID, TARGET)).thenReturn(false);

        FraudDecision decisao = useCase.execute(request(new BigDecimal("100.00")));

        assertThat(decisao).isEqualTo(FraudDecision.APPROVED);

        ArgumentCaptor<FraudEvaluation> captor = ArgumentCaptor.forClass(FraudEvaluation.class);
        verify(fraudAuditRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(FraudDecision.APPROVED);
        assertThat(captor.getValue().getScore()).isZero();
    }

    @Test
    void execute_valorAltoEDestinoNovo_deveRetornarSuspicious() {
        // HighValueRule: +40, NewDestinationRule: +25 — total: 65 → SUSPICIOUS
        when(fraudContextRepository.countTransfersLast24h(USER_ID)).thenReturn(1);
        when(fraudContextRepository.isNewDestination(USER_ID, TARGET)).thenReturn(true);

        FraudDecision decisao = useCase.execute(request(new BigDecimal("6000.00")));

        assertThat(decisao).isEqualTo(FraudDecision.SUSPICIOUS);

        ArgumentCaptor<FraudEvaluation> captor = ArgumentCaptor.forClass(FraudEvaluation.class);
        verify(fraudAuditRepository).save(captor.capture());
        FraudEvaluation avaliacao = captor.getValue();
        assertThat(avaliacao.getDecision()).isEqualTo(FraudDecision.SUSPICIOUS);
        assertThat(avaliacao.getScore()).isEqualTo(65);
    }

    @Test
    void execute_todasRegrasDisparadas_deveRetornarBlocked() {
        // HighValueRule: +40, HighFrequencyRule: +35, NewDestinationRule: +25 — total: 100 → BLOCKED
        when(fraudContextRepository.countTransfersLast24h(USER_ID)).thenReturn(6);
        when(fraudContextRepository.isNewDestination(USER_ID, TARGET)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request(new BigDecimal("6000.00"))))
                .isInstanceOf(FraudBlockedException.class);

        ArgumentCaptor<FraudEvaluation> captor = ArgumentCaptor.forClass(FraudEvaluation.class);
        verify(fraudAuditRepository).save(captor.capture());
        FraudEvaluation avaliacao = captor.getValue();
        assertThat(avaliacao.getDecision()).isEqualTo(FraudDecision.BLOCKED);
        assertThat(avaliacao.getScore()).isEqualTo(100);
        assertThat(avaliacao.getTriggeredRules()).containsExactlyInAnyOrder(
                "HighValueRule", "HighFrequencyRule", "NewDestinationRule");
    }

    @Test
    void execute_apenasAltaFrequencia_deveRetornarSuspicious() {
        // HighFrequencyRule: +35 — total: 35 → SUSPICIOUS
        when(fraudContextRepository.countTransfersLast24h(USER_ID)).thenReturn(5);
        when(fraudContextRepository.isNewDestination(USER_ID, TARGET)).thenReturn(false);

        FraudDecision decisao = useCase.execute(request(new BigDecimal("100.00")));

        assertThat(decisao).isEqualTo(FraudDecision.SUSPICIOUS);

        ArgumentCaptor<FraudEvaluation> captor = ArgumentCaptor.forClass(FraudEvaluation.class);
        verify(fraudAuditRepository).save(captor.capture());
        FraudEvaluation avaliacao = captor.getValue();
        assertThat(avaliacao.getDecision()).isEqualTo(FraudDecision.SUSPICIOUS);
        assertThat(avaliacao.getScore()).isEqualTo(35);
        assertThat(avaliacao.getTriggeredRules()).containsExactly("HighFrequencyRule");
    }

    @Test
    void execute_blocked_deveLancarFraudBlockedExceptionComTransferIdEScore() {
        // Garante que a excecao carrega os dados corretos para o GlobalExceptionHandler
        when(fraudContextRepository.countTransfersLast24h(USER_ID)).thenReturn(6);
        when(fraudContextRepository.isNewDestination(USER_ID, TARGET)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request(new BigDecimal("6000.00"))))
                .isInstanceOf(FraudBlockedException.class)
                .satisfies(ex -> {
                    FraudBlockedException fbe = (FraudBlockedException) ex;
                    assertThat(fbe.getTransferId()).isEqualTo(TRANSFER_ID);
                    assertThat(fbe.getScore()).isEqualTo(100);
                });
    }

    @Test
    void execute_aprovado_triggeredRulesDeveEstarVazia() {
        when(fraudContextRepository.countTransfersLast24h(USER_ID)).thenReturn(0);
        when(fraudContextRepository.isNewDestination(USER_ID, TARGET)).thenReturn(false);

        useCase.execute(request(new BigDecimal("50.00")));

        ArgumentCaptor<FraudEvaluation> captor = ArgumentCaptor.forClass(FraudEvaluation.class);
        verify(fraudAuditRepository).save(captor.capture());
        assertThat(captor.getValue().getTriggeredRules()).isEmpty();
    }
}
