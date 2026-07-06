package com.nexusbank.payments.adapter.in.messaging;

import com.nexusbank.payments.application.usecase.CompleteTransferUseCase;
import com.nexusbank.payments.application.usecase.CompensateTransferUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Listener da saga de pagamento.
 * Consome TransferInitiated e aciona o passo de crédito (CompleteTransferUseCase).
 * Em caso de falha irrecuperável aciona compensação (CompensateTransferUseCase).
 */
@Component
public class TransferSagaListener {

    private static final Logger log = LoggerFactory.getLogger(TransferSagaListener.class);

    private final CompleteTransferUseCase completeTransfer;
    private final CompensateTransferUseCase compensateTransfer;

    public TransferSagaListener(CompleteTransferUseCase completeTransfer,
                                CompensateTransferUseCase compensateTransfer) {
        this.completeTransfer = completeTransfer;
        this.compensateTransfer = compensateTransfer;
    }

    @KafkaListener(
            topics = "payments.transfer.initiated",
            groupId = "payments-saga",
            containerFactory = "sagaKafkaListenerContainerFactory"
    )
    public void onTransferInitiated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String transferId = record.key();
        log.info("Saga: recebido TransferInitiated para transferId={}", transferId);
        try {
            completeTransfer.execute(transferId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Saga: falha ao completar transferência id={} — iniciando compensação. Causa: {}",
                    transferId, e.getMessage(), e);
            try {
                compensateTransfer.execute(transferId, "Falha no crédito: " + e.getMessage());
                ack.acknowledge();
            } catch (Exception compensationEx) {
                log.error("Saga: compensação também falhou para id={}. Mensagem permanece no broker.",
                        transferId, compensationEx);
                // Não faz ack: a mensagem será reprocessada pelo broker
            }
        }
    }
}
