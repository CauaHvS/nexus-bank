package com.nexusbank.corebanking.adapter.out.persistence;

import com.nexusbank.corebanking.domain.port.out.OutboxMessage;
import com.nexusbank.corebanking.domain.port.out.OutboxRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class CoreBankingOutboxPersistenceAdapter implements OutboxRepository {

    private final CoreBankingOutboxJpaRepository jpa;

    CoreBankingOutboxPersistenceAdapter(CoreBankingOutboxJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(String aggregateId, String eventType, String payload) {
        jpa.save(new CoreBankingOutboxJpaEntity(aggregateId, eventType, payload));
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
