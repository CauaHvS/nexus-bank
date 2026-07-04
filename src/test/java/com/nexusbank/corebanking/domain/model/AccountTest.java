package com.nexusbank.corebanking.domain.model;

import com.nexusbank.corebanking.domain.event.AccountOpened;
import com.nexusbank.corebanking.domain.event.BalanceUpdated;
import com.nexusbank.corebanking.domain.exception.AccountNotActiveException;
import com.nexusbank.corebanking.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Account")
class AccountTest {

    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        customerId = CustomerId.of(UUID.randomUUID().toString());
    }

    // -------------------------------------------------------------------------
    // open()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("open() deve criar conta ACTIVE com saldo zero")
    void open_createsActiveAccountWithZeroBalance() {
        Account account = Account.open(customerId, AccountType.CHECKING, Currency.BRL, "0001-1", "0001");

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance().isZero()).isTrue();
        assertThat(account.getBalance().currency()).isEqualTo(Currency.BRL);
    }

    @Test
    @DisplayName("open() deve emitir exatamente um AccountOpened")
    void open_emitsExactlyOneAccountOpenedEvent() {
        Account account = Account.open(customerId, AccountType.CHECKING, Currency.BRL, "0001-2", "0001");

        List<Object> events = account.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AccountOpened.class);

        AccountOpened event = (AccountOpened) events.get(0);
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.accountNumber()).isEqualTo("0001-2");
        assertThat(event.type()).isEqualTo(AccountType.CHECKING);
        assertThat(event.currency()).isEqualTo(Currency.BRL);
    }

    @Test
    @DisplayName("pullDomainEvents() deve limpar a lista interna de eventos")
    void open_pullDomainEvents_clearsEventList() {
        Account account = Account.open(customerId, AccountType.CHECKING, Currency.BRL, "0001-3", "0001");

        account.pullDomainEvents(); // consome os eventos
        List<Object> segundaLeitura = account.pullDomainEvents();

        assertThat(segundaLeitura).isEmpty();
    }

    // -------------------------------------------------------------------------
    // credit()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("credit() com valor positivo aumenta o saldo corretamente")
    void credit_withPositiveAmount_increasesBalance() {
        Account account = accountWith100Brl();

        account.credit(Money.of("50.00", Currency.BRL));

        assertThat(account.getBalance().amount()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("credit() emite BalanceUpdated com o novo saldo")
    void credit_emitsBalanceUpdatedEvent() {
        Account account = freshAccount();
        account.pullDomainEvents(); // descarta AccountOpened

        account.credit(Money.of("75.00", Currency.BRL));

        List<Object> events = account.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(BalanceUpdated.class);

        BalanceUpdated event = (BalanceUpdated) events.get(0);
        assertThat(event.newBalance().amount()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(event.accountId()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("credit() com zero lança IllegalArgumentException")
    void credit_withZeroAmount_throwsIllegalArgumentException() {
        Account account = freshAccount();

        assertThatThrownBy(() -> account.credit(Money.zero(Currency.BRL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    @DisplayName("credit() com valor negativo lança IllegalArgumentException")
    void credit_withNegativeAmount_throwsIllegalArgumentException() {
        Account account = freshAccount();

        assertThatThrownBy(() -> account.credit(Money.of("-10.00", Currency.BRL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    @DisplayName("credit() em conta bloqueada lança AccountNotActiveException")
    void credit_onBlockedAccount_throwsAccountNotActiveException() {
        Account account = freshAccount();
        account.block();

        assertThatThrownBy(() -> account.credit(Money.of("10.00", Currency.BRL)))
                .isInstanceOf(AccountNotActiveException.class);
    }

    // -------------------------------------------------------------------------
    // debit()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("debit() com saldo suficiente diminui o saldo corretamente")
    void debit_withSufficientFunds_decreasesBalance() {
        Account account = accountWith100Brl();

        account.debit(Money.of("30.00", Currency.BRL));

        assertThat(account.getBalance().amount()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    @DisplayName("debit() com saldo insuficiente lança InsufficientFundsException")
    void debit_withInsufficientFunds_throwsInsufficientFundsException() {
        Account account = accountWith100Brl();

        assertThatThrownBy(() -> account.debit(Money.of("150.00", Currency.BRL)))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("150");
    }

    @Test
    @DisplayName("debit() com valor exato do saldo zera o saldo (limite da invariante)")
    void debit_withExactBalance_setsBalanceToZero() {
        Account account = accountWith100Brl();

        account.debit(Money.of("100.00", Currency.BRL));

        assertThat(account.getBalance().isZero()).isTrue();
    }

    @Test
    @DisplayName("debit() em conta bloqueada lança AccountNotActiveException")
    void debit_onBlockedAccount_throwsAccountNotActiveException() {
        Account account = accountWith100Brl();
        account.block();

        assertThatThrownBy(() -> account.debit(Money.of("10.00", Currency.BRL)))
                .isInstanceOf(AccountNotActiveException.class);
    }

    // -------------------------------------------------------------------------
    // block() / activate()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("block() muda o status para BLOCKED")
    void block_changesStatusToBlocked() {
        Account account = freshAccount();

        account.block();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(account.isActive()).isFalse();
    }

    @Test
    @DisplayName("activate() após block() restaura status para ACTIVE")
    void activate_afterBlock_changesStatusToActive() {
        Account account = freshAccount();
        account.block();

        account.activate();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.isActive()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Account freshAccount() {
        return Account.open(customerId, AccountType.CHECKING, Currency.BRL,
                UUID.randomUUID().toString().substring(0, 8), "0001");
    }

    private Account accountWith100Brl() {
        Account account = freshAccount();
        account.pullDomainEvents(); // descarta AccountOpened
        account.credit(Money.of("100.00", Currency.BRL));
        account.pullDomainEvents(); // descarta BalanceUpdated do crédito inicial
        return account;
    }
}
