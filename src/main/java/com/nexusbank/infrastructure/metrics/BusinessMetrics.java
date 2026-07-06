package com.nexusbank.infrastructure.metrics;

import com.nexusbank.payments.domain.port.out.TransferMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics implements TransferMetricsPort {

    private final Counter transfersInitiated;
    private final Counter transfersCompleted;
    private final Counter transfersFailed;
    private final Counter transfersBlocked;
    private final Counter transfersUnderReview;
    private final Counter optimisticLockConflicts;

    public BusinessMetrics(MeterRegistry registry) {
        this.transfersInitiated = Counter.builder("nexusbank.transfers.initiated")
                .description("Total de transferências iniciadas")
                .register(registry);
        this.transfersCompleted = Counter.builder("nexusbank.transfers.completed")
                .description("Total de transferências concluídas")
                .register(registry);
        this.transfersFailed = Counter.builder("nexusbank.transfers.failed")
                .description("Total de transferências falhas (incluindo compensação)")
                .register(registry);
        this.transfersBlocked = Counter.builder("nexusbank.transfers.blocked_by_fraud")
                .description("Total de transferências bloqueadas pelo Fraud")
                .register(registry);
        this.transfersUnderReview = Counter.builder("nexusbank.transfers.under_review")
                .description("Total de transferências em revisão de fraude")
                .register(registry);
        this.optimisticLockConflicts = Counter.builder("nexusbank.transfers.optimistic_lock_conflicts")
                .description("Total de conflitos de lock otimista em contas")
                .register(registry);
    }

    public void transferInitiated()      { transfersInitiated.increment(); }
    public void transferCompleted()      { transfersCompleted.increment(); }
    public void transferFailed()         { transfersFailed.increment(); }
    public void transferBlocked()        { transfersBlocked.increment(); }
    public void transferUnderReview()    { transfersUnderReview.increment(); }
    public void optimisticLockConflict() { optimisticLockConflicts.increment(); }
}
