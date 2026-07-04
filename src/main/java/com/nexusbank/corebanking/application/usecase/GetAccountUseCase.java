package com.nexusbank.corebanking.application.usecase;

import com.nexusbank.corebanking.application.dto.AccountView;
import com.nexusbank.corebanking.domain.exception.AccountNotFoundException;
import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.CustomerId;
import com.nexusbank.corebanking.domain.port.out.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetAccountUseCase {

    private final AccountRepository accountRepository;

    public GetAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public AccountView findById(String accountId) {
        return accountRepository.findById(AccountId.of(accountId))
                .map(OpenAccountUseCase::toView)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public List<AccountView> findByCustomerId(String customerId) {
        return accountRepository.findByCustomerId(CustomerId.of(customerId))
                .stream().map(OpenAccountUseCase::toView).toList();
    }
}
