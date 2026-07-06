package com.nexusbank.payments.adapter.out.persistence;

import com.nexusbank.payments.domain.model.PaymentType;
import com.nexusbank.payments.domain.model.TransferStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers", schema = "payments")
class TransferJpaEntity {

    @Id
    UUID id;

    @Column(name = "source_account_id")
    UUID sourceAccountId;

    @Column(name = "target_account_id")
    UUID targetAccountId;

    @Column(precision = 19, scale = 2)
    BigDecimal amount;

    @Column(length = 3)
    String currency;

    @Enumerated(EnumType.STRING)
    PaymentType type;

    @Enumerated(EnumType.STRING)
    TransferStatus status;

    @Column(name = "idempotency_key", unique = true)
    String idempotencyKey;

    @Column(name = "failure_reason")
    String failureReason;

    @Column(name = "created_at")
    Instant createdAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "scheduled_for")
    Instant scheduledFor;

    protected TransferJpaEntity() {}

    TransferJpaEntity(UUID id, UUID source, UUID target, BigDecimal amount, String currency,
                      PaymentType type, TransferStatus status, String idempotencyKey,
                      String failureReason, Instant createdAt, Instant completedAt, Instant scheduledFor) {
        this.id = id;
        this.sourceAccountId = source;
        this.targetAccountId = target;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.scheduledFor = scheduledFor;
    }
}
