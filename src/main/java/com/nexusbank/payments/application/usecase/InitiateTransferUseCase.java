package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.corebanking.domain.exception.AccountConcurrentModificationException;
import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.fraud.FraudApi;
import com.nexusbank.fraud.FraudBlockedException;
import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.FraudEvaluationRequest;
import com.nexusbank.payments.application.dto.InitiateTransferCommand;
import com.nexusbank.payments.application.dto.TransferResult;
import com.nexusbank.payments.domain.exception.AccountAccessDeniedException;
import com.nexusbank.payments.domain.exception.DuplicateTransferException;
import com.nexusbank.payments.domain.model.IdempotencyKey;
import com.nexusbank.payments.domain.model.PaymentType;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferMetricsPort;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitiateTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateTransferUseCase.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final CoreBankingApi coreBankingApi;
    private final FraudApi fraudApi;
    private final ObjectMapper objectMapper;
    private final TransferMetricsPort metrics;
    private final Tracer tracer;

    public InitiateTransferUseCase(TransferRepository transferRepository,
                                   OutboxRepository outboxRepository,
                                   CoreBankingApi coreBankingApi,
                                   FraudApi fraudApi,
                                   ObjectMapper objectMapper,
                                   TransferMetricsPort metrics,
                                   Tracer tracer) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.coreBankingApi = coreBankingApi;
        this.fraudApi = fraudApi;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Transactional
    public TransferResult execute(InitiateTransferCommand command) {
        var key = new IdempotencyKey(command.idempotencyKey());

        // Idempotência: se já existe, retornar o resultado anterior
        var existing = transferRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            log.info("Transferência já processada: key={}", key);
            var t = existing.get();
            if (t.isPending()) throw new DuplicateTransferException(key.value());
            return TransferResult.from(t);
        }

        if (!coreBankingApi.isOwner(command.sourceAccountId(), command.authenticatedUserId())) {
            throw new AccountAccessDeniedException(command.sourceAccountId());
        }

        Money amount = Money.of(command.amount(), Currency.valueOf(command.currency()));

        // 1. Persistir transferência (status SCHEDULED ou PENDING dependendo do scheduledFor)
        Transfer transfer;
        try {
            transfer = Transfer.initiate(
                    command.sourceAccountId(), command.targetAccountId(),
                    amount, key, PaymentType.valueOf(command.type()), command.scheduledFor());
            transfer = transferRepository.save(transfer);
        } catch (AccountConcurrentModificationException e) {
            metrics.optimisticLockConflict();
            throw e;
        }

        // Transferências agendadas não avaliam fraude nem debitam agora — o job fará isso
        if (transfer.isScheduled()) {
            log.info("Transferência agendada: id={} para={}", transfer.getId(), transfer.getScheduledFor());
            return TransferResult.from(transfer);
        }

        // 2. Avaliação de fraude dentro de span customizado
        FraudDecision fraudDecision;
        Span fraudSpan = tracer.nextSpan().name("fraud.evaluate").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(fraudSpan)) {
            fraudDecision = fraudApi.evaluate(new FraudEvaluationRequest(
                    transfer.getId().value().toString(),
                    command.authenticatedUserId(),
                    command.sourceAccountId(),
                    command.targetAccountId(),
                    command.amount(),
                    command.type()
            ));
            fraudSpan.tag("fraud.decision", fraudDecision.name());
        } catch (FraudBlockedException e) {
            fraudSpan.tag("fraud.decision", FraudDecision.BLOCKED.name());
            metrics.transferBlocked();
            throw e;
        } finally {
            fraudSpan.end();
        }

        if (fraudDecision == FraudDecision.SUSPICIOUS) {
            transfer.markUnderReview();
            transferRepository.save(transfer);
            metrics.transferUnderReview();
            log.info("Transferência marcada como UNDER_REVIEW por suspeita de fraude: id={}", transfer.getId());
            return TransferResult.from(transfer);
        }

        // 3. Debitar conta de origem (apenas para decisão APPROVED)
        try {
            coreBankingApi.debit(command.sourceAccountId(), amount,
                    "Transferência " + transfer.getId() + " - débito");
        } catch (AccountConcurrentModificationException e) {
            metrics.optimisticLockConflict();
            throw e;
        }

        // 4. Publicar evento via Outbox (atomicamente com a transação)
        try {
            var events = transfer.pullDomainEvents();
            for (Object event : events) {
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(transfer.getId().toString(),
                        event.getClass().getSimpleName(), payload);
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar evento para Outbox", e);
        }

        metrics.transferInitiated();
        log.info("Transferência iniciada: id={} valor={} tipo={} origem={} destino={}",
                transfer.getId(), amount, transfer.getType(),
                command.sourceAccountId(), command.targetAccountId());

        return TransferResult.from(transfer);
    }
}
