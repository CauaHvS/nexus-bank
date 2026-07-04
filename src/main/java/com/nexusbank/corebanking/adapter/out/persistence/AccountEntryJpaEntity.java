package com.nexusbank.corebanking.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_entries", schema = "corebanking")
class AccountEntryJpaEntity {

    @Id
    UUID id;

    @Column(name = "account_id", nullable = false)
    UUID accountId;

    @Column(name = "type", nullable = false)
    String type;  // "DEBIT" | "CREDIT"

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @Column(name = "description", nullable = false)
    String description;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    BigDecimal balanceAfter;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    protected AccountEntryJpaEntity() {}

    AccountEntryJpaEntity(UUID id, UUID accountId, String type,
                           BigDecimal amount, String description,
                           BigDecimal balanceAfter, Instant occurredAt) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.balanceAfter = balanceAfter;
        this.occurredAt = occurredAt;
    }
}
