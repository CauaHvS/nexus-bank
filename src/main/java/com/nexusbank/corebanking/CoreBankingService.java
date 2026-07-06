package com.nexusbank.corebanking;

import com.nexusbank.corebanking.domain.exception.AccountConcurrentModificationException;
import com.nexusbank.corebanking.domain.exception.AccountNotFoundException;
import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.corebanking.domain.port.out.AccountRepository;
import com.nexusbank.corebanking.domain.port.out.StatementRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CoreBankingService implements CoreBankingApi {

    private final AccountRepository accountRepository;
    private final StatementRepository statementRepository;

    CoreBankingService(AccountRepository accountRepository,
                       StatementRepository statementRepository) {
        this.accountRepository = accountRepository;
        this.statementRepository = statementRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void debit(String accountId, Money amount, String description) {
        try {
            var id = AccountId.of(accountId);
            var account = accountRepository.findById(id)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            account.debit(amount);
            accountRepository.save(account);
            statementRepository.addEntry(id, "DEBIT", amount.amount(), description,
                    account.getBalance().amount());
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
            throw new AccountConcurrentModificationException(accountId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void credit(String accountId, Money amount, String description) {
        try {
            var id = AccountId.of(accountId);
            var account = accountRepository.findById(id)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            account.credit(amount);
            accountRepository.save(account);
            statementRepository.addEntry(id, "CREDIT", amount.amount(), description,
                    account.getBalance().amount());
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
            throw new AccountConcurrentModificationException(accountId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean accountExists(String accountId) {
        return accountRepository.findById(AccountId.of(accountId)).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwner(String accountId, String userId) {
        return accountRepository.findById(AccountId.of(accountId))
                .map(account -> account.getCustomerId().value().toString().equals(userId))
                .orElse(false);
    }
}
