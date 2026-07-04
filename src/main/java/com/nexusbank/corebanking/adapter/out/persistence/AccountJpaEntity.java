package com.nexusbank.corebanking.adapter.out.persistence;

import com.nexusbank.corebanking.domain.model.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts", schema = "corebanking")
class AccountJpaEntity {

    @Id
    UUID id;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @Column(name = "account_number", nullable = false, unique = true)
    String accountNumber;

    @Column(name = "agency", nullable = false)
    String agency;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    AccountType type;

    @Column(name = "currency", nullable = false)
    @Enumerated(EnumType.STRING)
    Currency currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    BigDecimal balance;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    protected AccountJpaEntity() {}

    AccountJpaEntity(UUID id, UUID customerId, String accountNumber, String agency,
                     AccountType type, Currency currency, BigDecimal balance,
                     AccountStatus status, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.agency = agency;
        this.type = type;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
        this.createdAt = createdAt;
    }
}
