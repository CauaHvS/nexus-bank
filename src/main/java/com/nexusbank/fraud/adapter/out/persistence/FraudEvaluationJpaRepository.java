package com.nexusbank.fraud.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface FraudEvaluationJpaRepository extends JpaRepository<FraudEvaluationJpaEntity, UUID> {
    Optional<FraudEvaluationJpaEntity> findByTransferId(String transferId);
}
