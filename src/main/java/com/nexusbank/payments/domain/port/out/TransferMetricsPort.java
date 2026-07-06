package com.nexusbank.payments.domain.port.out;

/**
 * Port de saída para métricas de transferência.
 * Desacopla o domínio de payments da implementação concreta de métricas (Micrometer/Prometheus).
 * A implementação fica em infrastructure.metrics.
 */
public interface TransferMetricsPort {
    void transferInitiated();
    void transferCompleted();
    void transferFailed();
    void transferBlocked();
    void transferUnderReview();
    void optimisticLockConflict();
}
