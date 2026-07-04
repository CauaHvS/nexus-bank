package com.nexusbank.corebanking.application.usecase;

import com.nexusbank.corebanking.application.dto.StatementResult;
import com.nexusbank.corebanking.domain.exception.AccountNotFoundException;
import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.port.out.AccountRepository;
import com.nexusbank.corebanking.domain.port.out.StatementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class GetStatementUseCase {

    private final StatementRepository statementRepository;
    private final AccountRepository accountRepository;

    public GetStatementUseCase(StatementRepository statementRepository,
                                AccountRepository accountRepository) {
        this.statementRepository = statementRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public StatementResult execute(String accountId, int page, int size,
                                    Optional<Instant> startDate, Optional<Instant> endDate) {
        AccountId id = AccountId.of(accountId);
        if (accountRepository.findById(id).isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
        return statementRepository.findStatement(id, startDate, endDate,
                PageRequest.of(page, Math.min(size, 100)));
    }
}
