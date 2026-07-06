package com.nexusbank.notifications.adapter.in.messaging;

import com.nexusbank.notifications.application.dto.DlqEntry;
import com.nexusbank.notifications.domain.port.out.DlqRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler que reprocessa mensagens da DLQ de notificações.
 * Tenta reprocessar registros com retry_count < maxRetryCount.
 * Se atingir o limite, marca como exhausted (requer intervenção manual).
 */
@Component
public class DlqReprocessorScheduler {

    private static final Logger log = LoggerFactory.getLogger(DlqReprocessorScheduler.class);

    private final DlqRepository dlqRepository;
    private final KafkaNotificationListener kafkaListener;
    private final int maxRetryCount;

    public DlqReprocessorScheduler(DlqRepository dlqRepository,
                                   KafkaNotificationListener kafkaListener,
                                   @Value("${notifications.dlq.max-retry-count:5}") int maxRetryCount) {
        this.dlqRepository = dlqRepository;
        this.kafkaListener = kafkaListener;
        this.maxRetryCount = maxRetryCount;
    }

    @Scheduled(fixedDelayString = "${notifications.dlq.reprocess-delay-ms:60000}")
    public void reprocess() {
        List<DlqEntry> pending = dlqRepository.findPendingForRetry(maxRetryCount);
        if (pending.isEmpty()) return;

        log.info("DLQ: {} mensagens para reprocessar", pending.size());

        for (DlqEntry entry : pending) {
            try {
                reprocessEntry(entry);
                log.info("DLQ: mensagem reprocessada com sucesso id={} topic={}", entry.id(), entry.topic());
            } catch (Exception e) {
                log.warn("DLQ: falha ao reprocessar id={} topic={}: {}", entry.id(), entry.topic(), e.getMessage());
                dlqRepository.incrementRetryCount(entry.id());

                if (entry.retryCount() + 1 >= maxRetryCount) {
                    log.error("DLQ: mensagem esgotou tentativas id={} topic={} — marcada como exhausted. Intervenção manual necessária.",
                            entry.id(), entry.topic());
                    dlqRepository.markExhausted(entry.id());
                }
            }
        }
    }

    private void reprocessEntry(DlqEntry entry) throws Exception {
        var record = new org.apache.kafka.clients.consumer.ConsumerRecord<String, String>(
                entry.topic(), 0, 0L, null, entry.payload());
        kafkaListener.onEvent(record);
    }
}
