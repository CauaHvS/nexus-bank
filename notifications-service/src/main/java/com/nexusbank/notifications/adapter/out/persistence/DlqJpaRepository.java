package com.nexusbank.notifications.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface DlqJpaRepository extends JpaRepository<DlqJpaEntity, UUID> {

    @Query("SELECT d FROM DlqJpaEntity d WHERE d.retryCount < :maxRetryCount AND d.exhausted = false ORDER BY d.createdAt ASC")
    List<DlqJpaEntity> findPendingForRetry(int maxRetryCount);

    @Modifying
    @Query("UPDATE DlqJpaEntity d SET d.retryCount = d.retryCount + 1, d.lastRetryAt = :now WHERE d.id = :id")
    void incrementRetryCount(UUID id, Instant now);

    @Modifying
    @Query("UPDATE DlqJpaEntity d SET d.exhausted = true WHERE d.id = :id")
    void markExhausted(UUID id);
}
