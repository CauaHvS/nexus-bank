package com.nexusbank.corebanking.adapter.out.persistence;

import com.nexusbank.corebanking.domain.model.*;
import com.nexusbank.corebanking.domain.port.out.AccountRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;

    AccountPersistenceAdapter(AccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Account save(Account account) {
        return toDomain(jpa.save(toEntity(account)));
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Account> findByCustomerId(CustomerId customerId) {
        return jpa.findByCustomerId(customerId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByCustomerIdAndType(CustomerId customerId, AccountType type) {
        return jpa.existsByCustomerIdAndTypeAndStatus(customerId.value(), type, AccountStatus.ACTIVE);
    }

    private AccountJpaEntity toEntity(Account a) {
        return new AccountJpaEntity(
                a.getId().value(), a.getCustomerId().value(),
                a.getAccountNumber(), a.getAgency(), a.getType(), a.getCurrency(),
                a.getBalance().amount(), a.getStatus(), a.getCreatedAt());
    }

    private Account toDomain(AccountJpaEntity e) {
        return Account.reconstitute(
                AccountId.of(e.id.toString()), CustomerId.of(e.customerId.toString()),
                e.accountNumber, e.agency, e.type, e.currency,
                Money.of(e.balance, e.currency), e.status, e.createdAt);
    }
}
