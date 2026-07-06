package com.nexusbank.payments.adapter.out.persistence;

import com.nexusbank.payments.domain.model.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, UUID> {

    Optional<TransferJpaEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM TransferJpaEntity t WHERE t.status = 'SCHEDULED' AND t.scheduledFor <= :now")
    List<TransferJpaEntity> findDueScheduled(@Param("now") Instant now);

    Optional<TransferJpaEntity> findByIdAndStatus(UUID id, TransferStatus status);

}
