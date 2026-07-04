package com.nexusbank.corebanking.adapter.in.web;

import com.nexusbank.corebanking.application.dto.AccountView;
import com.nexusbank.corebanking.application.dto.OpenAccountCommand;
import com.nexusbank.corebanking.application.usecase.GetAccountUseCase;
import com.nexusbank.corebanking.application.usecase.OpenAccountUseCase;
import com.nexusbank.corebanking.domain.model.AccountType;
import com.nexusbank.corebanking.domain.model.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final OpenAccountUseCase openAccount;
    private final GetAccountUseCase getAccount;

    public AccountController(OpenAccountUseCase openAccount, GetAccountUseCase getAccount) {
        this.openAccount = openAccount;
        this.getAccount = getAccount;
    }

    record OpenAccountRequest(
        @NotNull AccountType type,
        @NotNull Currency currency
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView open(@Valid @RequestBody OpenAccountRequest req,
                             @AuthenticationPrincipal String customerId) {
        return openAccount.execute(new OpenAccountCommand(customerId, req.type(), req.currency()));
    }

    @GetMapping("/{accountId}")
    public AccountView findById(@PathVariable String accountId) {
        return getAccount.findById(accountId);
    }

    @GetMapping
    public List<AccountView> findMine(@AuthenticationPrincipal String customerId) {
        return getAccount.findByCustomerId(customerId);
    }
}
