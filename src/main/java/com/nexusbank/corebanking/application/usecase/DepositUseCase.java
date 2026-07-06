package com.nexusbank.corebanking.application.usecase;

import com.nexusbank.corebanking.application.dto.BalanceView;
import com.nexusbank.corebanking.domain.exception.AccountNotFoundException;
import com.nexusbank.corebanking.domain.model.Account;
import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.corebanking.domain.port.out.AccountRepository;
import com.nexusbank.corebanking.domain.port.out.BalanceCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class DepositUseCase {

    private static final Logger log = LoggerFactory.getLogger(DepositUseCase.class);

    private final AccountRepository accountRepository;
    private final BalanceCache balanceCache;

    public DepositUseCase(AccountRepository accountRepository, BalanceCache balanceCache) {
        this.accountRepository = accountRepository;
        this.balanceCache = balanceCache;
    }

    @Transactional
    public BalanceView execute(String accountId, BigDecimal amount) {
        AccountId id = AccountId.of(accountId);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        Money money = Money.of(amount, account.getCurrency());
        account.credit(money);
        accountRepository.save(account);
        balanceCache.evict(id);

        log.info("Depósito realizado: accountId={}, valor={}", accountId, amount);
        return new BalanceView(id.value(), account.getBalance().amount(),
                account.getCurrency().name(), Instant.now());
    }
}
