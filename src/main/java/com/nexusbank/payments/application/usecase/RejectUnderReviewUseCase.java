package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Rejeita uma transferência em UNDER_REVIEW: move para FAILED sem debitar.
 * Publica TransferFailed no Outbox para notificar o usuário.
 */
@Service
public class RejectUnderReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(RejectUnderReviewUseCase.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RejectUnderReviewUseCase(TransferRepository transferRepository,
                                    OutboxRepository outboxRepository,
                                    ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void execute(String transferId, String reason) {
        var id = TransferId.of(transferId);
        var transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        if (!transfer.isUnderReview()) {
            throw new TransferNotUnderReviewException(transferId, transfer.getStatus().name());
        }

        String failureReason = "Rejeitado na revisão de fraude: " + reason;
        transfer.fail(failureReason);
        transferRepository.save(transfer);

        // Publica TransferFailed no Outbox para notificação
        try {
            for (Object event : transfer.pullDomainEvents()) {
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(transferId, event.getClass().getSimpleName(), payload);
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao publicar TransferFailed no Outbox após rejeição de fraude", e);
        }

        log.info("Transferência rejeitada na revisão de fraude: id={} motivo={}", transferId, reason);
    }
}
