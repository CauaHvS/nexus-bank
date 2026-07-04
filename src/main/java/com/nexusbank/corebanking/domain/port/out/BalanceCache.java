package com.nexusbank.corebanking.domain.port.out;

import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.Money;

import java.util.Optional;

public interface BalanceCache {

    void put(AccountId accountId, Money balance);

    Optional<Money> get(AccountId accountId);

    void evict(AccountId accountId);
}
