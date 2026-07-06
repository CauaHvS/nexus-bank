package com.nexusbank.notifications.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.notifications.application.usecase.CreateNotificationUseCase;
import com.nexusbank.notifications.domain.model.NotificationType;
import com.nexusbank.notifications.domain.port.out.DlqRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Testes unitários do KafkaNotificationListener.
 *
 * Estratégia: chama processEvent() diretamente para testar o comportamento de parsing
 * e roteamento sem activar os retries inline (3 tentativas × 500ms).
 * O teste de DLQ chama onEvent() com payload inválido — os retries disparam,
 * mas o ObjectMapper real falha na mesma tentativa (sem mock de sleepQuietly),
 * então o teste aguarda naturalmente os 3 retries (~1s total no worst case).
 */
@ExtendWith(MockitoExtension.class)
class KafkaNotificationListenerTest {

    @Mock
    private CreateNotificationUseCase createNotificationUseCase;

    @Mock
    private DlqRepository dlqRepository;

    private KafkaNotificationListener listener;

    // ObjectMapper real — JSON parsing deve ser testado com comportamento real
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        listener = new KafkaNotificationListener(createNotificationUseCase, dlqRepository, objectMapper);
    }

    @Test
    void onEvent_transferCompleted_deveCriarNotificacao() throws Exception {
        // Arrange — sourceAccountId é o userId usado para notificar o dono da conta
        String sourceAccountId = "account-source-001";
        String payload = """
                {
                    "sourceAccountId": "%s",
                    "targetAccountId": "account-target-002",
                    "amount": {
                        "amount": 250.00,
                        "currency": "BRL"
                    }
                }
                """.formatted(sourceAccountId);

        // Act — usa processEvent direto para evitar os retries e sleeps
        listener.processEvent("payments.transfer.completed", payload);

        // Assert
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);

        verify(createNotificationUseCase, times(1))
                .execute(userIdCaptor.capture(), typeCaptor.capture(), anyString(), anyString());

        assertThat(userIdCaptor.getValue()).isEqualTo(sourceAccountId);
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
    }

    @Test
    void onEvent_transferFailed_naoDeveCriarNotificacao() throws Exception {
        // Conforme implementação: handleTransferFailed apenas loga, sem userId disponível
        // — createNotificationUseCase NÃO deve ser chamado
        String payload = """
                {
                    "transferId": {"value": "transfer-abc-999"},
                    "reason": "Saldo insuficiente"
                }
                """;

        // Act
        listener.processEvent("payments.transfer.failed", payload);

        // Assert — use case NUNCA chamado (evolução futura quando evento tiver sourceAccountId)
        verify(createNotificationUseCase, never()).execute(anyString(), any(), anyString(), anyString());
    }

    @Test
    void onEvent_accountOpened_deveCriarNotificacao() throws Exception {
        // Arrange — customerId serializado como objeto {"value":"uuid"} (record Java)
        String customerUuid = "cust-uuid-789";
        String accountNumber = "0001234-5";
        String payload = """
                {
                    "customerId": {"value": "%s"},
                    "accountNumber": "%s",
                    "status": "ACTIVE"
                }
                """.formatted(customerUuid, accountNumber);

        // Act
        listener.processEvent("corebanking.account.opened", payload);

        // Assert
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        verify(createNotificationUseCase, times(1))
                .execute(userIdCaptor.capture(), typeCaptor.capture(), anyString(), bodyCaptor.capture());

        assertThat(userIdCaptor.getValue()).isEqualTo(customerUuid);
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.ACCOUNT_OPENED);
        assertThat(bodyCaptor.getValue()).contains(accountNumber);
    }

    @Test
    void onEvent_payloadInvalido_deveSalvarNaDlq() {
        // Arrange — payload JSON malformado: objectMapper.readTree lança JsonProcessingException
        String payloadMalformado = "{ isto nao e json valido :::";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "payments.transfer.completed", 0, 0L, null, payloadMalformado);

        // Act — chama onEvent para acionar o loop de retries completo
        // O listener fará 3 tentativas e então gravará na DLQ
        listener.onEvent(record);

        // Assert
        verify(dlqRepository, times(1)).save(
                eq("payments.transfer.completed"),
                eq(payloadMalformado),
                anyString());
        verify(createNotificationUseCase, never()).execute(anyString(), any(), anyString(), anyString());
    }

    @Test
    void onEvent_topicoDesconhecido_naoDeveCriarNotificacaoNemDlq() throws Exception {
        // Arrange — tópico não registrado no switch
        String payload = """
                {"qualquerCampo": "qualquerValor"}
                """;

        // Act
        listener.processEvent("topico.desconhecido", payload);

        // Assert
        verify(createNotificationUseCase, never()).execute(anyString(), any(), anyString(), anyString());
        verify(dlqRepository, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void onEvent_transferCompleted_amountComoValorSimples_deveParsear() throws Exception {
        // Cobre o branch: amountNode não é objeto, é valor numérico simples
        String sourceAccountId = "account-simple-amount";
        String payload = """
                {
                    "sourceAccountId": "%s",
                    "amount": 150.00
                }
                """.formatted(sourceAccountId);

        // Act
        listener.processEvent("payments.transfer.completed", payload);

        // Assert
        verify(createNotificationUseCase, times(1))
                .execute(eq(sourceAccountId), eq(NotificationType.TRANSFER_COMPLETED), anyString(), anyString());
    }
}
