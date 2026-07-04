package com.nexusbank.corebanking.application.usecase;

import com.nexusbank.corebanking.adapter.out.persistence.AccountNumberGenerator;
import com.nexusbank.corebanking.application.dto.AccountView;
import com.nexusbank.corebanking.application.dto.OpenAccountCommand;
import com.nexusbank.corebanking.domain.exception.AccountAlreadyExistsException;
import com.nexusbank.corebanking.domain.model.*;
import com.nexusbank.corebanking.domain.port.out.AccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenAccountUseCase {

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator numberGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public OpenAccountUseCase(AccountRepository accountRepository,
                               AccountNumberGenerator numberGenerator,
                               ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.numberGenerator = numberGenerator;
        this.eventPublisher = eventPublisher;
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
        saved.pullDomainEvents().forEach(eventPublisher::publishEvent);

        return toView(saved);
    }

    static AccountView toView(Account a) {
        return new AccountView(
                a.getId().value(), a.getAccountNumber(), a.getAgency(),
                a.getType(), a.getCurrency(), a.getBalance().amount(), a.getStatus());
    }
}
