package com.nexusbank.corebanking.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.corebanking.adapter.out.persistence.AccountNumberGenerator;
import com.nexusbank.corebanking.application.dto.AccountView;
import com.nexusbank.corebanking.application.dto.OpenAccountCommand;
import com.nexusbank.corebanking.domain.event.AccountOpened;
import com.nexusbank.corebanking.domain.exception.AccountAlreadyExistsException;
import com.nexusbank.corebanking.domain.model.*;
import com.nexusbank.corebanking.domain.port.out.AccountRepository;
import com.nexusbank.corebanking.domain.port.out.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OpenAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(OpenAccountUseCase.class);

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator numberGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OpenAccountUseCase(AccountRepository accountRepository,
                               AccountNumberGenerator numberGenerator,
                               ApplicationEventPublisher eventPublisher,
                               OutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.numberGenerator = numberGenerator;
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccountView execute(OpenAccountCommand command) {
        CustomerId customerId = CustomerId.of(command.customerId());

        if (accountRepository.existsByCustomerIdAndType(customerId, command.type())) {
            throw new AccountAlreadyExistsException(command.customerId(), command.type());
        }

        Account account = Account.open(customerId, command.type(), command.currency(),
                numberGenerator.generate(), "0001");
        Account saved = accountRepository.save(account);

        List<Object> events = saved.pullDomainEvents();

        // Publica in-process para listeners locais (ex: BalanceProjectionListener)
        events.forEach(eventPublisher::publishEvent);

        // Publica AccountOpened via Outbox para entrega no Kafka ao módulo Notifications
        events.stream()
                .filter(e -> e instanceof AccountOpened)
                .map(e -> (AccountOpened) e)
                .forEach(e -> publishToOutbox(saved, e));

        return toView(saved);
    }

    private void publishToOutbox(Account account, AccountOpened event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(
                    account.getId().value().toString(),
                    "AccountOpened",
                    payload);
            log.debug("AccountOpened publicado no Outbox: accountId={}", account.getId().value());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar AccountOpened para o Outbox", e);
        }
    }

    static AccountView toView(Account a) {
        return new AccountView(
                a.getId().value(), a.getAccountNumber(), a.getAgency(),
                a.getType(), a.getCurrency(), a.getBalance().amount(), a.getStatus());
    }
}
