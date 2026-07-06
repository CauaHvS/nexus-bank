package com.nexusbank.fraud;

/**
 * Lançada pelo EvaluateFraudUseCase quando o score atinge ou supera 70.
 * Faz parte da API pública do módulo Fraud (pacote raiz) para permitir
 * que o GlobalExceptionHandler e o módulo Payments a capturem.
 * Mapeada para HTTP 422 no GlobalExceptionHandler.
 */
public class FraudBlockedException extends RuntimeException {

    private final String transferId;
    private final int score;

    public FraudBlockedException(String transferId, int score) {
        super("Transferência bloqueada por análise de risco. Score: " + score);
        this.transferId = transferId;
        this.score = score;
    }

    public String getTransferId() { return transferId; }
    public int getScore()         { return score; }
}
