package com.nexusbank.payments;

import com.nexusbank.payments.application.usecase.ApproveUnderReviewUseCase;
import com.nexusbank.payments.application.usecase.RejectUnderReviewUseCase;
import org.springframework.stereotype.Service;

/**
 * Implementação da API pública do módulo Payments.
 * Ponto de entrada para outros módulos alterarem estado de transferências.
 */
@Service
class PaymentsService implements PaymentsApi {

    private final ApproveUnderReviewUseCase approveUseCase;
    private final RejectUnderReviewUseCase rejectUseCase;

    PaymentsService(ApproveUnderReviewUseCase approveUseCase,
                    RejectUnderReviewUseCase rejectUseCase) {
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
    }

    @Override
    public void approveUnderReview(String transferId) {
        approveUseCase.execute(transferId);
    }

    @Override
    public void rejectUnderReview(String transferId, String reason) {
        rejectUseCase.execute(transferId, reason);
    }
}
