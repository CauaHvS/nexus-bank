package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Job que processa transferências agendadas cujo scheduledFor chegou.
 * Executa a cada payments.scheduler.fixed-delay ms (padrão: 60s).
 */
@Component
public class ScheduledTransferJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferJob.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final CoreBankingApi coreBankingApi;
    private final ObjectMapper objectMapper;

    public ScheduledTransferJob(TransferRepository transferRepository,
                                OutboxRepository outboxRepository,
                                CoreBankingApi coreBankingApi,
                                ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.coreBankingApi = coreBankingApi;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${payments.scheduler.fixed-delay:60000}")
    public void processScheduledTransfers() {
        List<Transfer> due = transferRepository.findDueScheduled(Instant.now());
        if (due.isEmpty()) return;

        log.info("Processando {} transferências agendadas", due.size());

        for (Transfer transfer : due) {
            try {
                activateScheduled(transfer);
            } catch (Exception e) {
                log.error("Falha ao processar transferência agendada {}: {}",
                        transfer.getId(), e.getMessage());
            }
        }
    }

    /**
     * Ativa uma transferência agendada: muda status para PENDING,
     * debita a conta de origem e registra evento no Outbox — tudo na mesma transação.
     */
    @Transactional
    public void activateScheduled(Transfer transfer) {
        // Ativa o agregado (SCHEDULED -> PENDING, gera evento TransferInitiated)
        transfer.activate();

        // Persiste antes do débito para garantir idempotência via status
        transferRepository.save(transfer);

        // Debita a conta de origem
        coreBankingApi.debit(transfer.getSourceAccountId(), transfer.getAmount(),
                "Transferência agendada " + transfer.getId() + " - débito");

        // Publica evento no Outbox atomicamente
        try {
            var events = transfer.pullDomainEvents();
            for (Object event : events) {
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(transfer.getId().toString(),
                        event.getClass().getSimpleName(), payload);
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar evento para Outbox na transferência agendada", e);
        }

        log.info("Transferência agendada ativada: id={} valor={} origem={} destino={}",
                transfer.getId(), transfer.getAmount(),
                transfer.getSourceAccountId(), transfer.getTargetAccountId());
    }
}
