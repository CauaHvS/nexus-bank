package com.nexusbank.payments.application.usecase;

import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.payments.application.dto.TransferResult;
import com.nexusbank.payments.domain.exception.AccountAccessDeniedException;
import com.nexusbank.payments.domain.exception.TransferNotFoundException;
import com.nexusbank.payments.domain.model.TransferId;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetTransferUseCase.class);

    private final TransferRepository transferRepository;
    private final CoreBankingApi coreBankingApi;

    public GetTransferUseCase(TransferRepository transferRepository, CoreBankingApi coreBankingApi) {
        this.transferRepository = transferRepository;
        this.coreBankingApi = coreBankingApi;
    }

    @Transactional(readOnly = true)
    public TransferResult execute(String transferId, String authenticatedUserId) {
        var id = TransferId.of(transferId);
        var transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        boolean ownsSource = coreBankingApi.isOwner(transfer.getSourceAccountId(), authenticatedUserId);
        boolean ownsTarget = coreBankingApi.isOwner(transfer.getTargetAccountId(), authenticatedUserId);

        if (!ownsSource && !ownsTarget) {
            log.warn("Acesso negado à transferência id={} por usuario={}", transferId, authenticatedUserId);
            throw new AccountAccessDeniedException(transferId);
        }

        return TransferResult.from(transfer);
    }
}
