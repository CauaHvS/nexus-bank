package com.nexusbank.payments.adapter.out.persistence;

import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.payments.domain.model.*;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class TransferPersistenceAdapter implements TransferRepository {

    private final TransferJpaRepository jpa;

    TransferPersistenceAdapter(TransferJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Transfer save(Transfer t) {
        return toDomain(jpa.save(toEntity(t)));
    }

    @Override
    public Optional<Transfer> findById(TransferId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Transfer> findByIdempotencyKey(IdempotencyKey key) {
        return jpa.findByIdempotencyKey(key.value()).map(this::toDomain);
    }

    private TransferJpaEntity toEntity(Transfer t) {
        return new TransferJpaEntity(
                t.getId().value(),
                UUID.fromString(t.getSourceAccountId()),
                UUID.fromString(t.getTargetAccountId()),
                t.getAmount().amount(),
                t.getAmount().currency().name(),
                t.getType(),
                t.getStatus(),
                t.getIdempotencyKey().value(),
                t.getFailureReason(),
                t.getCreatedAt(),
                t.getCompletedAt());
    }

    private Transfer toDomain(TransferJpaEntity e) {
        return Transfer.reconstitute(
                TransferId.of(e.id.toString()),
                e.sourceAccountId.toString(),
                e.targetAccountId.toString(),
                Money.of(e.amount, Currency.valueOf(e.currency)),
                new IdempotencyKey(e.idempotencyKey),
                e.type,
                e.status,
                e.failureReason,
                e.createdAt,
                e.completedAt);
    }
}
