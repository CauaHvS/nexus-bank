package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.payments.application.dto.InitiateTransferCommand;
import com.nexusbank.payments.application.dto.TransferResult;
import com.nexusbank.payments.domain.exception.DuplicateTransferException;
import com.nexusbank.payments.domain.model.IdempotencyKey;
import com.nexusbank.payments.domain.model.PaymentType;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitiateTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateTransferUseCase.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final CoreBankingApi coreBankingApi;
    private final ObjectMapper objectMapper;

    public InitiateTransferUseCase(TransferRepository transferRepository,
                                   OutboxRepository outboxRepository,
                                   CoreBankingApi coreBankingApi,
                                   ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.coreBankingApi = coreBankingApi;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransferResult execute(InitiateTransferCommand command) {
        var key = new IdempotencyKey(command.idempotencyKey());

        // Idempotência: se já existe, retornar o resultado anterior
        var existing = transferRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            log.info("Transferência já processada: key={}", key);
            var t = existing.get();
            if (t.isPending()) throw new DuplicateTransferException(key.value());
            return TransferResult.from(t);
        }

        Money amount = Money.of(command.amount(), Currency.valueOf(command.currency()));

        // 1. Persistir transferência como PENDING (na mesma transação)
        Transfer transfer = Transfer.initiate(
                command.sourceAccountId(), command.targetAccountId(),
                amount, key, PaymentType.valueOf(command.type()));
        transfer = transferRepository.save(transfer);

        // 2. Debitar conta de origem (ainda na mesma transação)
        coreBankingApi.debit(command.sourceAccountId(), amount,
                "Transferência " + transfer.getId() + " - débito");

        // 3. Publicar evento via Outbox (atomicamente com a transação)
        try {
            var events = transfer.pullDomainEvents();
            for (Object event : events) {
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(transfer.getId().toString(),
                        event.getClass().getSimpleName(), payload);
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar evento para Outbox", e);
        }

        log.info("Transferência iniciada: id={} valor={} origem={} destino={}",
                transfer.getId(), amount, command.sourceAccountId(), command.targetAccountId());

        return TransferResult.from(transfer);
    }
}
