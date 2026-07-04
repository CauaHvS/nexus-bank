package com.nexusbank.corebanking.domain.port.out;

import com.nexusbank.corebanking.domain.model.Account;
import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.AccountType;
import com.nexusbank.corebanking.domain.model.CustomerId;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(AccountId id);

    List<Account> findByCustomerId(CustomerId customerId);

    boolean existsByCustomerIdAndType(CustomerId customerId, AccountType type);
}
