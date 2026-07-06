package com.nexusbank.notifications.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.notifications.application.usecase.CreateNotificationUseCase;
import com.nexusbank.notifications.domain.model.NotificationType;
import com.nexusbank.notifications.domain.port.out.DlqRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Consumidor Kafka do módulo Notifications.
 * Processa eventos de transferência e abertura de conta, criando notificações in-app.
 * Em caso de falha após 3 tentativas, grava na DLQ e comita o offset (não trava o tópico).
 */
@Component
public class KafkaNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationListener.class);
    private static final int MAX_INLINE_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500L;

    private final CreateNotificationUseCase createNotificationUseCase;
    private final DlqRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public KafkaNotificationListener(CreateNotificationUseCase createNotificationUseCase,
                                     DlqRepository dlqRepository,
                                     ObjectMapper objectMapper) {
        this.createNotificationUseCase = createNotificationUseCase;
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"payments.transfer.completed", "payments.transfer.failed", "corebanking.account.opened"},
            groupId = "${spring.kafka.consumer.group-id:notifications-service}")
    public void onEvent(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        String payload = record.value();

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_INLINE_RETRIES; attempt++) {
            try {
                processEvent(topic, payload);
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("Tentativa {}/{} falhou para tópico {}: {}", attempt, MAX_INLINE_RETRIES, topic, e.getMessage());
                if (attempt < MAX_INLINE_RETRIES) {
                    sleepQuietly(RETRY_DELAY_MS);
                }
            }
        }

        log.error("Falha após {} tentativas para tópico {}. Enviando para DLQ.", MAX_INLINE_RETRIES, topic);
        saveToDlq(topic, payload, lastException != null ? lastException.getMessage() : "Erro desconhecido");
        // Offset é comitado pelo Spring Kafka após retorno do método — tópico não trava
    }

    void processEvent(String topic, String payload) throws Exception {
        JsonNode data = objectMapper.readTree(payload);

        switch (topic) {
            case "payments.transfer.completed" -> handleTransferCompleted(data);
            case "payments.transfer.failed"    -> handleTransferFailed(data);
            case "corebanking.account.opened"  -> handleAccountOpened(data);
            default -> log.warn("Tópico desconhecido ignorado: {}", topic);
        }
    }

    private void handleTransferCompleted(JsonNode data) {
        // sourceAccountId é String no record TransferCompleted
        String sourceAccountId = data.path("sourceAccountId").asText();
        BigDecimal amount = extractAmount(data);
        String currency = extractCurrency(data);

        // Notificação vai para o dono da conta de origem
        createNotificationUseCase.execute(
                sourceAccountId,
                NotificationType.TRANSFER_COMPLETED,
                "Transferência concluída",
                String.format("Sua transferência de %s %.2f foi concluída com sucesso.", currency, amount));
    }

    private void handleTransferFailed(JsonNode data) {
        // TransferFailed não carrega userId diretamente — usamos o aggregateId (transferId)
        // O sourceAccountId está no Transfer, mas o payload de TransferFailed só tem transferId e reason.
        // Sem userId não há como criar a notificação para o usuário correto — evento é logado.
        String reason = data.path("reason").asText("Motivo não informado");
        JsonNode transferIdNode = data.path("transferId");
        String transferId = transferIdNode.isObject()
                ? transferIdNode.path("value").asText()
                : transferIdNode.asText();
        log.warn("TransferFailed recebido (transferId={} reason={}) — sem userId no payload, notificação não criada.",
                transferId, reason);
        // Evolução futura: enriquecer o evento TransferFailed com sourceAccountId/userId.
    }

    private void handleAccountOpened(JsonNode data) {
        // customerId é serializado como {"value":"uuid"} por ser um record Java
        JsonNode customerIdNode = data.path("customerId");
        String userId = customerIdNode.isObject()
                ? customerIdNode.path("value").asText()
                : customerIdNode.asText();

        String accountNumber = data.path("accountNumber").asText();

        createNotificationUseCase.execute(
                userId,
                NotificationType.ACCOUNT_OPENED,
                "Conta aberta com sucesso",
                String.format("Sua conta %s foi aberta e está pronta para uso.", accountNumber));
    }

    private BigDecimal extractAmount(JsonNode data) {
        JsonNode amountNode = data.path("amount");
        if (amountNode.isObject()) {
            return amountNode.path("amount").decimalValue();
        }
        return amountNode.decimalValue();
    }

    private String extractCurrency(JsonNode data) {
        JsonNode amountNode = data.path("amount");
        if (amountNode.isObject()) {
            return amountNode.path("currency").asText("BRL");
        }
        return "BRL";
    }

    private void saveToDlq(String topic, String payload, String errorMessage) {
        try {
            dlqRepository.save(topic, payload, errorMessage);
        } catch (Exception e) {
            log.error("Falha ao gravar na DLQ (topic={}): {}", topic, e.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
