package com.nexusbank.payments.domain.port.out;

import java.util.List;

public interface OutboxRepository {
    void save(String aggregateId, String eventType, String payload);
    List<OutboxMessage> findUnpublished();
    void markPublished(Long id);
}
