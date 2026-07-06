package com.nexusbank.corebanking;

import com.nexusbank.corebanking.domain.model.Money;

/**
 * API pública do módulo CoreBanking.
 * Único ponto de entrada para outros módulos interagirem com contas.
 */
public interface CoreBankingApi {
    void debit(String accountId, Money amount, String description);
    void credit(String accountId, Money amount, String description);
    boolean accountExists(String accountId);
    boolean isOwner(String accountId, String userId);
}
