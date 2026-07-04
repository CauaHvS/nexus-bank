package com.nexusbank.corebanking.domain.model;

import com.nexusbank.corebanking.domain.event.AccountOpened;
import com.nexusbank.corebanking.domain.event.BalanceUpdated;
import com.nexusbank.corebanking.domain.exception.AccountNotActiveException;
import com.nexusbank.corebanking.domain.exception.InsufficientFundsException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregado raiz do contexto Core Banking.
 *
 * Invariantes:
 *   - Saldo nunca fica negativo (exceto se limite de crédito for implementado no futuro).
 *   - Débito e crédito só permitidos em contas ACTIVE.
 *   - Saldo inicial é sempre zero.
 */
public class Account {

    private final AccountId id;
    private final CustomerId customerId;
    private final String accountNumber;
    private final String agency;
    private final AccountType type;
    private final Currency currency;
    private Money balance;
    private AccountStatus status;
    private final Instant createdAt;
    private final List<Object> domainEvents = new ArrayList<>();

    private Account(AccountId id, CustomerId customerId, String accountNumber, String agency,
                    AccountType type, Currency currency, Money balance,
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

    public static Account open(CustomerId customerId, AccountType type, Currency currency,
                               String accountNumber, String agency) {
        Money zero = Money.zero(currency);
        Account account = new Account(
                AccountId.generate(), customerId, accountNumber, agency,
                type, currency, zero, AccountStatus.ACTIVE, Instant.now());
        account.domainEvents.add(new AccountOpened(
                account.id, account.customerId, account.accountNumber,
                account.type, account.currency, account.createdAt));
        return account;
    }

    public void debit(Money amount) {
        assertActive();
        Money newBalance = balance.subtract(amount);
        if (newBalance.isNegative()) throw new InsufficientFundsException(balance, amount);
        balance = newBalance;
        domainEvents.add(new BalanceUpdated(id, balance, Instant.now()));
    }

    public void credit(Money amount) {
        assertActive();
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("Valor de crédito deve ser positivo");
        }
        balance = balance.add(amount);
        domainEvents.add(new BalanceUpdated(id, balance, Instant.now()));
    }

    public void block() {
        this.status = AccountStatus.BLOCKED;
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    /** Factory para reconstituição a partir da persistência — não emite eventos. */
    public static Account reconstitute(AccountId id, CustomerId customerId, String accountNumber,
                                       String agency, AccountType type, Currency currency,
                                       Money balance, AccountStatus status, Instant createdAt) {
        return new Account(id, customerId, accountNumber, agency, type, currency,
                balance, status, createdAt);
    }

    private void assertActive() {
        if (!isActive()) throw new AccountNotActiveException(id, status);
    }

    public AccountId getId()          { return id; }
    public CustomerId getCustomerId() { return customerId; }
    public String getAccountNumber()  { return accountNumber; }
    public String getAgency()         { return agency; }
    public AccountType getType()      { return type; }
    public Currency getCurrency()     { return currency; }
    public Money getBalance()         { return balance; }
    public AccountStatus getStatus()  { return status; }
    public Instant getCreatedAt()     { return createdAt; }
}
