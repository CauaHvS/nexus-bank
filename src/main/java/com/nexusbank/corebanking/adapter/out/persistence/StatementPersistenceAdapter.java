package com.nexusbank.corebanking.adapter.out.persistence;

import com.nexusbank.corebanking.application.dto.StatementResult;
import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.port.out.StatementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
class StatementPersistenceAdapter implements StatementRepository {

    private final AccountEntryJpaRepository jpa;

    StatementPersistenceAdapter(AccountEntryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StatementResult findStatement(AccountId accountId, Optional<Instant> start,
                                          Optional<Instant> end, Pageable pageable) {
        UUID id = accountId.value();
        Page<AccountEntryJpaEntity> page = (start.isPresent() && end.isPresent())
                ? jpa.findByAccountIdAndOccurredAtBetweenOrderByOccurredAtDesc(id, start.get(), end.get(), pageable)
                : jpa.findByAccountIdOrderByOccurredAtDesc(id, pageable);

        return new StatementResult(
                page.getContent().stream().map(e -> new StatementResult.Entry(
                        e.id, e.type, e.amount, e.description, e.occurredAt, e.balanceAfter
                )).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public void addEntry(AccountId accountId, String type, BigDecimal amount,
                          String description, BigDecimal balanceAfter) {
        jpa.save(new AccountEntryJpaEntity(
                UUID.randomUUID(), accountId.value(),
                type, amount, description, balanceAfter, Instant.now()));
    }
}
