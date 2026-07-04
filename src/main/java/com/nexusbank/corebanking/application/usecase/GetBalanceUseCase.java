package com.nexusbank.corebanking.application.usecase;

import com.nexusbank.corebanking.application.dto.BalanceView;
import com.nexusbank.corebanking.domain.exception.AccountNotFoundException;
import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.port.out.AccountRepository;
import com.nexusbank.corebanking.domain.port.out.BalanceCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class GetBalanceUseCase {

    private final AccountRepository accountRepository;
    private final BalanceCache balanceCache;

    public GetBalanceUseCase(AccountRepository accountRepository, BalanceCache balanceCache) {
        this.accountRepository = accountRepository;
        this.balanceCache = balanceCache;
    }

    @Transactional(readOnly = true)
    public BalanceView execute(String accountId) {
        AccountId id = AccountId.of(accountId);

        // Cache hit
        return balanceCache.get(id).map(money ->
                new BalanceView(id.value(), money.amount(), money.currency().name(), Instant.now())
        ).orElseGet(() -> {
            // Cache miss: busca no banco e popula o cache
            var account = accountRepository.findById(id)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            balanceCache.put(id, account.getBalance());
            return new BalanceView(id.value(), account.getBalance().amount(),
                    account.getCurrency().name(), account.getCreatedAt());
        });
    }
}
