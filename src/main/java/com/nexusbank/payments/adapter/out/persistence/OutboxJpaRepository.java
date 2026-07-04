package com.nexusbank.payments.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {

    @Query("SELECT o FROM OutboxJpaEntity o WHERE o.published = false ORDER BY o.createdAt ASC")
    List<OutboxJpaEntity> findUnpublished();

    @Modifying
    @Query("UPDATE OutboxJpaEntity o SET o.published = true, o.publishedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void markPublished(Long id);
}
