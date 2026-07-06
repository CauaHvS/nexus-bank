package com.nexusbank.notifications.adapter.out.persistence;

import com.nexusbank.notifications.application.dto.DlqEntry;
import com.nexusbank.notifications.domain.port.out.DlqRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
class DlqPersistenceAdapter implements DlqRepository {

    private final DlqJpaRepository jpa;

    DlqPersistenceAdapter(DlqJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(String topic, String payload, String errorMessage) {
        jpa.save(new DlqJpaEntity(topic, payload, errorMessage));
    }

    @Override
    public List<DlqEntry> findPendingForRetry(int maxRetryCount) {
        return jpa.findPendingForRetry(maxRetryCount).stream()
                .map(e -> new DlqEntry(e.id, e.topic, e.payload, e.retryCount))
                .toList();
    }

    @Override
    public void incrementRetryCount(UUID id) {
        jpa.incrementRetryCount(id, Instant.now());
    }

    @Override
    public void markExhausted(UUID id) {
        jpa.markExhausted(id);
    }
}
