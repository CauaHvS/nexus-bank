package com.nexusbank.corebanking.domain.exception;

import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.AccountStatus;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(AccountId id, AccountStatus status) {
        super("Conta " + id + " não está ativa: status " + status);
    }
}
