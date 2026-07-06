package com.nexusbank.payments.application.usecase;

import com.nexusbank.payments.domain.exception.TransferNotFoundException;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.model.TransferId;
import com.nexusbank.payments.domain.model.TransferStatus;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelScheduledTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelScheduledTransferUseCase.class);

    private final TransferRepository transferRepository;

    public CancelScheduledTransferUseCase(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Transactional
    public void execute(String transferId, String authenticatedUserId) {
        // Verifica se a transferência existe em qualquer status
        Transfer transfer = transferRepository.findById(TransferId.of(transferId))
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        // Verifica se está no status correto para cancelamento
        if (!transfer.isScheduled()) {
            throw new IllegalStateException("Transferência não está agendada");
        }

        transfer.fail("Cancelado pelo usuário");
        transferRepository.save(transfer);

        log.info("Transferência agendada cancelada: id={} usuário={}", transferId, authenticatedUserId);
    }
}
