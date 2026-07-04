package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.payments.domain.exception.TransferNotFoundException;
import com.nexusbank.payments.domain.model.TransferId;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompensateTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompensateTransferUseCase.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final CoreBankingApi coreBankingApi;
    private final ObjectMapper objectMapper;

    public CompensateTransferUseCase(TransferRepository transferRepository,
                                     OutboxRepository outboxRepository,
                                     CoreBankingApi coreBankingApi,
                                     ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.coreBankingApi = coreBankingApi;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void execute(String transferId, String reason) {
        var id = TransferId.of(transferId);
        var transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        if (!transfer.isPending()) {
            log.warn("Tentativa de compensar transferência não-PENDING: id={}", id);
            return;
        }

        try {
            // Estornar débito na origem
            coreBankingApi.credit(transfer.getSourceAccountId(), transfer.getAmount(),
                    "Estorno transferência " + transferId);
            transfer.fail(reason);
        } catch (Exception compensationEx) {
            log.error("ALERTA: Falha na compensação da transferência {}. Intervenção manual necessária.",
                    transferId, compensationEx);
            transfer.markCompensationFailed("Falha na compensação: " + compensationEx.getMessage());
        }

        transferRepository.save(transfer);

        try {
            for (Object event : transfer.pullDomainEvents()) {
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(transferId, event.getClass().getSimpleName(), payload);
            }
        } catch (Exception e) {
            log.error("Falha ao publicar evento de compensação no Outbox: {}", transferId, e);
        }

        log.info("Compensação executada: id={} motivo={}", transferId, reason);
    }
}
