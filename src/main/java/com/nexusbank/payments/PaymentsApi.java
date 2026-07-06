package com.nexusbank.payments;

/**
 * API pública do módulo Payments.
 * Único ponto de entrada para outros módulos alterarem estado de transferências.
 *
 * Nota: countTransfersLast24h e isNewDestination foram removidos desta interface —
 * o módulo Fraud os acessa diretamente via FraudContextPersistenceAdapter (JdbcTemplate),
 * eliminando a dependência bidirecional Fraud <-> Payments.
 */
public interface PaymentsApi {

    /**
     * Aprova uma transferência em UNDER_REVIEW: move para PENDING, executa débito
     * e aciona o fluxo de crédito (CompleteTransferUseCase).
     * Lança TransferNotFoundException se transferId não existir.
     * Lança TransferNotUnderReviewException se status atual não for UNDER_REVIEW.
     */
    void approveUnderReview(String transferId);

    /**
     * Rejeita uma transferência em UNDER_REVIEW: move para FAILED sem debitar.
     * Lança TransferNotFoundException se transferId não existir.
     * Lança TransferNotUnderReviewException se status atual não for UNDER_REVIEW.
     */
    void rejectUnderReview(String transferId, String reason);
}
