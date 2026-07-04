package com.nexusbank.payments.adapter.out.persistence;

import com.nexusbank.payments.domain.port.out.OutboxMessage;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class OutboxPersistenceAdapter implements OutboxRepository {

    private final OutboxJpaRepository jpa;

    OutboxPersistenceAdapter(OutboxJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(String aggregateId, String eventType, String payload) {
        jpa.save(new OutboxJpaEntity(aggregateId, eventType, payload));
    }

    @Override
    public List<OutboxMessage> findUnpublished() {
        return jpa.findUnpublished().stream()
                .map(e -> new OutboxMessage(e.id, e.aggregateId, e.eventType, e.payload))
                .toList();
    }

    @Override
    public void markPublished(Long id) {
        jpa.markPublished(id);
    }
}
