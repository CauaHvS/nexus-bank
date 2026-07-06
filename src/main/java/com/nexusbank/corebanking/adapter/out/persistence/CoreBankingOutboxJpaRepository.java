package com.nexusbank.corebanking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface CoreBankingOutboxJpaRepository extends JpaRepository<CoreBankingOutboxJpaEntity, Long> {

    @Query("SELECT o FROM CoreBankingOutboxJpaEntity o WHERE o.published = false ORDER BY o.createdAt ASC")
    List<CoreBankingOutboxJpaEntity> findUnpublished();

    @Modifying
    @Query("UPDATE CoreBankingOutboxJpaEntity o SET o.published = true, o.publishedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void markPublished(Long id);
}
