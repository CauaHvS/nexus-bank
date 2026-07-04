package com.nexusbank.corebanking.adapter.in.messaging;

import com.nexusbank.corebanking.domain.event.BalanceUpdated;
import com.nexusbank.corebanking.domain.port.out.BalanceCache;
import com.nexusbank.corebanking.domain.port.out.StatementRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Projeta eventos de domínio no read model de extrato e invalida o cache de saldo.
 * Roda no mesmo processo (in-process event); Kafka será usado para eventos cross-module.
 */
@Component
public class BalanceProjectionListener {

    private final StatementRepository statementRepository;
    private final BalanceCache balanceCache;

    public BalanceProjectionListener(StatementRepository statementRepository,
                                      BalanceCache balanceCache) {
        this.statementRepository = statementRepository;
        this.balanceCache = balanceCache;
    }

    @EventListener
    public void on(BalanceUpdated event) {
        // Invalida o cache — a próxima leitura vai ao banco e repopula
        balanceCache.evict(event.accountId());
        // A entrada no extrato é adicionada pelo use case de escrita (debit/credit),
        // que possui contexto do tipo da operação e descrição.
    }
}
