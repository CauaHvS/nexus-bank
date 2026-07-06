package com.nexusbank.payments.domain.exception;

/**
 * Lançada quando uma operação de revisão (approve/reject) é solicitada
 * para uma transferência que não está em UNDER_REVIEW.
 * Mapeada para HTTP 409 no GlobalExceptionHandler.
 */
public class TransferNotUnderReviewException extends RuntimeException {

    private final String transferId;
    private final String currentStatus;

    public TransferNotUnderReviewException(String transferId, String currentStatus) {
        super("Transferência " + transferId + " não está em UNDER_REVIEW. Status atual: " + currentStatus);
        this.transferId = transferId;
        this.currentStatus = currentStatus;
    }

    public String getTransferId()    { return transferId; }
    public String getCurrentStatus() { return currentStatus; }
}
