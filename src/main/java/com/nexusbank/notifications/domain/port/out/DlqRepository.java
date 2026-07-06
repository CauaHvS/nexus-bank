package com.nexusbank.notifications.domain.port.out;

import com.nexusbank.notifications.application.dto.DlqEntry;

import java.util.List;
import java.util.UUID;

public interface DlqRepository {
    void save(String topic, String payload, String errorMessage);
    List<DlqEntry> findPendingForRetry(int maxRetryCount);
    void incrementRetryCount(UUID id);
    void markExhausted(UUID id);
}
