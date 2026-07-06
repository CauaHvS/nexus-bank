package com.nexusbank.payments.adapter.in.web;

import com.nexusbank.payments.application.dto.InitiateTransferCommand;
import com.nexusbank.payments.application.dto.TransferResult;
import com.nexusbank.payments.application.usecase.CancelScheduledTransferUseCase;
import com.nexusbank.payments.application.usecase.GetTransferUseCase;
import com.nexusbank.payments.application.usecase.InitiateTransferUseCase;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

@Tag(name = "Transferências")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final InitiateTransferUseCase initiateTransfer;
    private final GetTransferUseCase getTransfer;
    private final CancelScheduledTransferUseCase cancelScheduledTransfer;

    public TransferController(InitiateTransferUseCase initiateTransfer,
                              GetTransferUseCase getTransfer,
                              CancelScheduledTransferUseCase cancelScheduledTransfer) {
        this.initiateTransfer = initiateTransfer;
        this.getTransfer = getTransfer;
        this.cancelScheduledTransfer = cancelScheduledTransfer;
    }

    record TransferRequest(
            @NotBlank String sourceAccountId,
            @NotBlank String targetAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String type,
            String description,
            Instant scheduledFor
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Bulkhead(name = "transferInitiate", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "bulkheadFallback")
    @RateLimiter(name = "transferEndpoint", fallbackMethod = "rateLimitFallback")
    public TransferResult initiate(
            @Valid @RequestBody TransferRequest req,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @AuthenticationPrincipal String authenticatedUserId) {
        return initiateTransfer.execute(new InitiateTransferCommand(
                req.sourceAccountId(), req.targetAccountId(), req.amount(),
                req.currency(), req.type(), idempotencyKey, req.description(),
                authenticatedUserId, req.scheduledFor()));
    }

    TransferResult rateLimitFallback(
            TransferRequest req, String idempotencyKey, String authenticatedUserId,
            RequestNotPermitted ex) {
        throw ex;
    }

    TransferResult bulkheadFallback(
            TransferRequest req, String idempotencyKey, String authenticatedUserId,
            BulkheadFullException ex) {
        throw ex;
    }

    @GetMapping("/{transferId}")
    public TransferResult getTransfer(
            @PathVariable String transferId,
            @AuthenticationPrincipal String authenticatedUserId) {
        return getTransfer.execute(transferId, authenticatedUserId);
    }

    @DeleteMapping("/{transferId}/schedule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelScheduled(
            @PathVariable String transferId,
            @AuthenticationPrincipal String authenticatedUserId) {
        cancelScheduledTransfer.execute(transferId, authenticatedUserId);
    }
}
