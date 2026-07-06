package com.nexusbank.corebanking.adapter.in.web;

import com.nexusbank.corebanking.application.dto.AccountView;
import com.nexusbank.corebanking.application.dto.BalanceView;
import com.nexusbank.corebanking.application.dto.OpenAccountCommand;
import com.nexusbank.corebanking.application.dto.StatementResult;
import com.nexusbank.corebanking.application.usecase.DepositUseCase;
import com.nexusbank.corebanking.application.usecase.GetAccountUseCase;
import com.nexusbank.corebanking.application.usecase.GetBalanceUseCase;
import com.nexusbank.corebanking.application.usecase.GetStatementUseCase;
import com.nexusbank.corebanking.application.usecase.OpenAccountUseCase;
import com.nexusbank.corebanking.domain.model.AccountType;
import com.nexusbank.corebanking.domain.model.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Tag(name = "Contas")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final OpenAccountUseCase openAccount;
    private final GetAccountUseCase getAccount;
    private final GetBalanceUseCase getBalance;
    private final GetStatementUseCase getStatement;
    private final DepositUseCase deposit;

    public AccountController(OpenAccountUseCase openAccount, GetAccountUseCase getAccount,
                              GetBalanceUseCase getBalance, GetStatementUseCase getStatement,
                              DepositUseCase deposit) {
        this.openAccount = openAccount;
        this.getAccount = getAccount;
        this.getBalance = getBalance;
        this.getStatement = getStatement;
        this.deposit = deposit;
    }

    record OpenAccountRequest(
        @NotNull AccountType type,
        @NotNull Currency currency
    ) {}

    record DepositRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Valor mínimo de depósito é R$ 0,01")
        BigDecimal amount
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

    @GetMapping("/{accountId}/balance")
    public BalanceView balance(@PathVariable String accountId) {
        return getBalance.execute(accountId);
    }

    @PostMapping("/{accountId}/deposit")
    public BalanceView deposit(@PathVariable String accountId,
                               @Valid @RequestBody DepositRequest req) {
        return deposit.execute(accountId, req.amount());
    }

    @GetMapping("/{accountId}/statement")
    public StatementResult statement(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {
        return getStatement.execute(accountId, page, size,
                Optional.ofNullable(startDate), Optional.ofNullable(endDate));
    }
}
