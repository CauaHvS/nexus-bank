package com.nexusbank.payments.adapter.in.web;

import com.nexusbank.payments.application.dto.InitiateTransferCommand;
import com.nexusbank.payments.application.dto.TransferResult;
import com.nexusbank.payments.application.usecase.InitiateTransferUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final InitiateTransferUseCase initiateTransfer;

    public TransferController(InitiateTransferUseCase initiateTransfer) {
        this.initiateTransfer = initiateTransfer;
    }

    record TransferRequest(
            @NotBlank String sourceAccountId,
            @NotBlank String targetAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String type,
            String description
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResult initiate(
            @Valid @RequestBody TransferRequest req,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @AuthenticationPrincipal String authenticatedUserId) {
        return initiateTransfer.execute(new InitiateTransferCommand(
                req.sourceAccountId(), req.targetAccountId(), req.amount(),
                req.currency(), req.type(), idempotencyKey, req.description()));
    }
}
