package com.nexusbank.payments.domain.model;

import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.payments.domain.event.TransferCompleted;
import com.nexusbank.payments.domain.event.TransferFailed;
import com.nexusbank.payments.domain.event.TransferInitiated;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregado raiz do módulo Payments.
 * Máquina de estados: SCHEDULED -> PENDING -> COMPLETED | FAILED.
 * Transferências sem scheduledFor (ou no passado) iniciam como PENDING diretamente.
 */
public class Transfer {

    private final TransferId id;
    private final String sourceAccountId;
    private final String targetAccountId;
    private final Money amount;
    private final IdempotencyKey idempotencyKey;
    private final PaymentType type;
    private TransferStatus status;
    private String failureReason;
    private final Instant createdAt;
    private Instant completedAt;
    private final Instant scheduledFor;
    private final List<Object> domainEvents = new ArrayList<>();

    private Transfer(TransferId id, String sourceAccountId, String targetAccountId,
                     Money amount, IdempotencyKey idempotencyKey, PaymentType type,
                     TransferStatus status, Instant createdAt, Instant scheduledFor) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.scheduledFor = scheduledFor;
    }

    /**
     * Cria uma transferência imediata (sem agendamento).
     */
    public static Transfer initiate(String sourceAccountId, String targetAccountId,
                                    Money amount, IdempotencyKey idempotencyKey,
                                    PaymentType type) {
        return initiate(sourceAccountId, targetAccountId, amount, idempotencyKey, type, null);
    }

    /**
     * Cria uma transferência, agendada se scheduledFor for não nulo e no futuro.
     */
    public static Transfer initiate(String sourceAccountId, String targetAccountId,
                                    Money amount, IdempotencyKey idempotencyKey,
                                    PaymentType type, Instant scheduledFor) {
        boolean isScheduled = scheduledFor != null && scheduledFor.isAfter(Instant.now());
        TransferStatus initialStatus = isScheduled ? TransferStatus.SCHEDULED : TransferStatus.PENDING;

        Transfer t = new Transfer(TransferId.generate(), sourceAccountId, targetAccountId,
                amount, idempotencyKey, type, initialStatus, Instant.now(), scheduledFor);

        if (!isScheduled) {
            t.domainEvents.add(new TransferInitiated(t.id, sourceAccountId, targetAccountId,
                    amount, idempotencyKey, t.createdAt));
        }
        return t;
    }

    /**
     * Ativa uma transferência agendada, movendo-a para PENDING e gerando o evento de domínio.
     */
    public void activate() {
        if (this.status != TransferStatus.SCHEDULED)
            throw new IllegalStateException("Apenas transferências SCHEDULED podem ser ativadas");
        this.status = TransferStatus.PENDING;
        domainEvents.add(new TransferInitiated(id, sourceAccountId, targetAccountId,
                amount, idempotencyKey, Instant.now()));
    }

    public void complete() {
        if (this.status != TransferStatus.PENDING)
            throw new IllegalStateException("Apenas transferências PENDING podem ser completadas");
        this.status = TransferStatus.COMPLETED;
        this.completedAt = Instant.now();
        domainEvents.add(new TransferCompleted(id, sourceAccountId, targetAccountId,
                amount, completedAt));
    }

    public void fail(String reason) {
        if (this.status != TransferStatus.PENDING && this.status != TransferStatus.SCHEDULED)
            throw new IllegalStateException("Apenas transferências PENDING ou SCHEDULED podem falhar");
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
        domainEvents.add(new TransferFailed(id, reason, Instant.now()));
    }

    public void markCompensationFailed(String reason) {
        this.status = TransferStatus.COMPENSATION_FAILED;
        this.failureReason = reason;
    }

    public boolean isPending()    { return status == TransferStatus.PENDING; }
    public boolean isCompleted()  { return status == TransferStatus.COMPLETED; }
    public boolean isScheduled()  { return status == TransferStatus.SCHEDULED; }

    public List<Object> pullDomainEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public static Transfer reconstitute(TransferId id, String sourceAccountId, String targetAccountId,
                                        Money amount, IdempotencyKey idempotencyKey, PaymentType type,
                                        TransferStatus status, String failureReason,
                                        Instant createdAt, Instant completedAt, Instant scheduledFor) {
        Transfer t = new Transfer(id, sourceAccountId, targetAccountId, amount,
                idempotencyKey, type, status, createdAt, scheduledFor);
        t.failureReason = failureReason;
        t.completedAt = completedAt;
        return t;
    }

    public TransferId getId()                    { return id; }
    public String getSourceAccountId()           { return sourceAccountId; }
    public String getTargetAccountId()           { return targetAccountId; }
    public Money getAmount()                     { return amount; }
    public IdempotencyKey getIdempotencyKey()    { return idempotencyKey; }
    public PaymentType getType()                 { return type; }
    public TransferStatus getStatus()            { return status; }
    public String getFailureReason()             { return failureReason; }
    public Instant getCreatedAt()                { return createdAt; }
    public Instant getCompletedAt()              { return completedAt; }
    public Instant getScheduledFor()             { return scheduledFor; }
}
