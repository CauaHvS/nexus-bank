package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.payments.domain.exception.TransferNotFoundException;
import com.nexusbank.payments.domain.exception.TransferNotUnderReviewException;
import com.nexusbank.payments.domain.model.TransferId;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprova uma transferência em UNDER_REVIEW: executa o débito e move para PENDING,
 * publicando TransferInitiated no Outbox para que o CompleteTransferUseCase
 * seja acionado pelo consumidor Kafka.
 */
@Service
public class ApproveUnderReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveUnderReviewUseCase.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final CoreBankingApi coreBankingApi;
    private final ObjectMapper objectMapper;

    public ApproveUnderReviewUseCase(TransferRepository transferRepository,
                                     OutboxRepository outboxRepository,
                                     CoreBankingApi coreBankingApi,
                                     ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.coreBankingApi = coreBankingApi;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void execute(String transferId) {
        var id = TransferId.of(transferId);
        var transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        if (!transfer.isUnderReview()) {
            throw new TransferNotUnderReviewException(transferId, transfer.getStatus().name());
        }

        // Move para PENDING e emite TransferInitiated
        transfer.activateFromReview();

        // Debita a conta de origem (ocorre atomicamente na transação)
        coreBankingApi.debit(transfer.getSourceAccountId(), transfer.getAmount(),
                "Transferência " + transferId + " - débito (aprovada na revisão de fraude)");

        transferRepository.save(transfer);

        // Publica TransferInitiated no Outbox para acionar o crédito via Kafka/Saga
        try {
            for (Object event : transfer.pullDomainEvents()) {
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(transferId, event.getClass().getSimpleName(), payload);
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao publicar evento no Outbox após aprovação de fraude", e);
        }

        log.info("Transferência aprovada na revisão de fraude: id={}", transferId);
    }
}
