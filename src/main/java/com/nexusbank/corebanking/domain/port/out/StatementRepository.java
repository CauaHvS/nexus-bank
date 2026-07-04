package com.nexusbank.corebanking.domain.port.out;

import com.nexusbank.corebanking.application.dto.StatementResult;
import com.nexusbank.corebanking.domain.model.AccountId;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface StatementRepository {

    StatementResult findStatement(AccountId accountId, Optional<Instant> start,
                                   Optional<Instant> end, Pageable pageable);

    void addEntry(AccountId accountId, String type, BigDecimal amount,
                  String description, BigDecimal balanceAfter);
}
