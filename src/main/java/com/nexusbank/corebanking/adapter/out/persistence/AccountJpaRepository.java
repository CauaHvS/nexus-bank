package com.nexusbank.corebanking.adapter.out.persistence;

import com.nexusbank.corebanking.domain.model.AccountStatus;
import com.nexusbank.corebanking.domain.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {
    List<AccountJpaEntity> findByCustomerId(UUID customerId);
    boolean existsByCustomerIdAndTypeAndStatus(UUID customerId, AccountType type, AccountStatus status);
}
