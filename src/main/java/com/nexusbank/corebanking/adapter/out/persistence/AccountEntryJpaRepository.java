package com.nexusbank.corebanking.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.UUID;

interface AccountEntryJpaRepository extends JpaRepository<AccountEntryJpaEntity, UUID> {

    Page<AccountEntryJpaEntity> findByAccountIdOrderByOccurredAtDesc(UUID accountId, Pageable pageable);

    Page<AccountEntryJpaEntity> findByAccountIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID accountId, Instant start, Instant end, Pageable pageable);
}
