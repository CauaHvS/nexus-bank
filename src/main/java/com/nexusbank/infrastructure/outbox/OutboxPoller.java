package com.nexusbank.infrastructure.outbox;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Poller que lê eventos não-publicados dos Outboxes de todos os módulos produtores e os envia ao Kafka.
 * Roda a cada 2 segundos. Garante entrega at-least-once.
 * Suporta payments e corebanking como módulos produtores.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final com.nexusbank.payments.domain.port.out.OutboxRepository paymentsOutbox;
    private final com.nexusbank.corebanking.domain.port.out.OutboxRepository coreBankingOutbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(com.nexusbank.payments.domain.port.out.OutboxRepository paymentsOutbox,
                        com.nexusbank.corebanking.domain.port.out.OutboxRepository coreBankingOutbox,
                        KafkaTemplate<String, String> kafkaTemplate) {
        this.paymentsOutbox = paymentsOutbox;
        this.coreBankingOutbox = coreBankingOutbox;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void poll() {
        pollRepository(paymentsOutbox);
        pollCoreBankingRepository(coreBankingOutbox);
    }

    private void pollRepository(com.nexusbank.payments.domain.port.out.OutboxRepository repository) {
        List<com.nexusbank.payments.domain.port.out.OutboxMessage> unpublished = repository.findUnpublished();
        if (unpublished.isEmpty()) return;

        log.debug("Outbox payments: {} eventos para publicar", unpublished.size());

        for (com.nexusbank.payments.domain.port.out.OutboxMessage entry : unpublished) {
            String topic = resolveTopicFor(entry.eventType());
            try {
                publishToKafka(topic, entry.aggregateId(), entry.payload());
                repository.markPublished(entry.id());
                log.debug("Evento payments publicado: tipo={} aggregateId={} topic={}",
                        entry.eventType(), entry.aggregateId(), topic);
            } catch (Exception e) {
                // Falha já logada no fallback; evento permanece no outbox para nova tentativa
            }
        }
    }

    private void pollCoreBankingRepository(com.nexusbank.corebanking.domain.port.out.OutboxRepository repository) {
        List<com.nexusbank.corebanking.domain.port.out.OutboxMessage> unpublished = repository.findUnpublished();
        if (unpublished.isEmpty()) return;

        log.debug("Outbox corebanking: {} eventos para publicar", unpublished.size());

        for (com.nexusbank.corebanking.domain.port.out.OutboxMessage entry : unpublished) {
            String topic = resolveTopicFor(entry.eventType());
            try {
                publishToKafka(topic, entry.aggregateId(), entry.payload());
                repository.markPublished(entry.id());
                log.debug("Evento corebanking publicado: tipo={} aggregateId={} topic={}",
                        entry.eventType(), entry.aggregateId(), topic);
            } catch (Exception e) {
                // Falha já logada no fallback; evento permanece no outbox para nova tentativa
            }
        }
    }

    @Retry(name = "outboxPublish", fallbackMethod = "publishFallback")
    @CircuitBreaker(name = "kafkaPublish", fallbackMethod = "publishFallback")
    public void publishToKafka(String topic, String key, String payload) throws Exception {
        kafkaTemplate.send(topic, key, payload).get();
    }

    void publishFallback(String topic, String key, String payload, Exception ex) {
        log.warn("Falha ao publicar no Kafka (será reprocessado): topic={} key={} causa={}",
                topic, key, ex.getMessage());
        // Não marca como publicado: o evento permanece no outbox para nova tentativa
    }

    private String resolveTopicFor(String eventType) {
        return switch (eventType) {
            case "TransferInitiated"   -> "payments.transfer.initiated";
            case "TransferCompleted"   -> "payments.transfer.completed";
            case "TransferFailed"      -> "payments.transfer.failed";
            case "TransferCompensated" -> "payments.transfer.compensated";
            case "AccountOpened"       -> "corebanking.account.opened";
            default -> "events.unknown";
        };
    }
}
