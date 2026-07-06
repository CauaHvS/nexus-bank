package com.nexusbank.infrastructure.outbox;

import com.nexusbank.payments.domain.port.out.OutboxMessage;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
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
 * Poller que lê eventos não-publicados do Outbox e os envia ao Kafka.
 * Roda a cada 2 segundos. Garante entrega at-least-once.
 * A publicação individual usa Retry + CircuitBreaker do Resilience4j.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxRepository outboxRepository,
                        KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void poll() {
        List<OutboxMessage> unpublished = outboxRepository.findUnpublished();
        if (unpublished.isEmpty()) return;

        log.debug("Outbox: {} eventos para publicar", unpublished.size());

        for (OutboxMessage entry : unpublished) {
            String topic = resolveTopicFor(entry.eventType());
            try {
                publishToKafka(topic, entry.aggregateId(), entry.payload());
                outboxRepository.markPublished(entry.id());
                log.debug("Evento publicado: tipo={} aggregateId={} topic={}",
                        entry.eventType(), entry.aggregateId(), topic);
            } catch (Exception e) {
                // Falha já foi logada no fallback; evento permanece no outbox para nova tentativa
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
            default -> "payments.events";
        };
    }
}
