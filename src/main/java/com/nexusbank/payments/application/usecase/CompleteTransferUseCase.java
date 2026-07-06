package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.payments.domain.exception.TransferNotFoundException;
import com.nexusbank.payments.domain.model.TransferId;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferMetricsPort;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteTransferUseCase.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final CoreBankingApi coreBankingApi;
    private final ObjectMapper objectMapper;
    private final TransferMetricsPort metrics;

    public CompleteTransferUseCase(TransferRepository transferRepository,
                                   OutboxRepository outboxRepository,
                                   CoreBankingApi coreBankingApi,
                                   ObjectMapper objectMapper,
                                   TransferMetricsPort metrics) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.coreBankingApi = coreBankingApi;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(String transferId) {
        var id = TransferId.of(transferId);
        var transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        if (!transfer.isPending()) {
            log.warn("Tentativa de completar transferência não-PENDING: id={} status={}", id, transfer.getStatus());
            return;
        }

        // Creditar conta destino
        coreBankingApi.credit(transfer.getTargetAccountId(), transfer.getAmount(),
                "Transferência " + transferId + " - crédito");

        transfer.complete();
        transferRepository.save(transfer);

        // Publicar TransferCompleted via Outbox
        try {
            for (Object event : transfer.pullDomainEvents()) {
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(transferId, event.getClass().getSimpleName(), payload);
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao publicar TransferCompleted no Outbox", e);
        }

        metrics.transferCompleted();
        log.info("Transferência concluída: id={}", transferId);
    }
}
