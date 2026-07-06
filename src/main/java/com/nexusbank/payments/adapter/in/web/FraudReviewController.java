package com.nexusbank.payments.adapter.in.web;

import com.nexusbank.payments.application.usecase.ApproveUnderReviewUseCase;
import com.nexusbank.payments.application.usecase.RejectUnderReviewUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoints de revisão manual de transferências em UNDER_REVIEW.
 *
 * Ficam no módulo Payments (e não no Fraud) para evitar ciclo de módulo:
 * esses endpoints alteram diretamente o estado de transferências via
 * ApproveUnderReviewUseCase e RejectUnderReviewUseCase, ambos internos ao Payments.
 * Ver ADR-011.
 */
@Tag(name = "Fraude")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/fraud/reviews")
class FraudReviewController {

    private final ApproveUnderReviewUseCase approveUseCase;
    private final RejectUnderReviewUseCase rejectUseCase;

    FraudReviewController(ApproveUnderReviewUseCase approveUseCase,
                          RejectUnderReviewUseCase rejectUseCase) {
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
    }

    @PostMapping("/{transferId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Map<String, Object>> approve(@PathVariable String transferId) {
        approveUseCase.execute(transferId);
        return ResponseEntity.ok(Map.of(
                "transferId", transferId,
                "newStatus", "PENDING",
                "reviewedAt", Instant.now().toString()));
    }

    @PostMapping("/{transferId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Map<String, Object>> reject(
            @PathVariable String transferId,
            @RequestBody(required = false) RejectReviewRequest body) {
        String reason = body != null && body.reason() != null
                ? body.reason()
                : "Rejeitado por revisão manual";
        rejectUseCase.execute(transferId, reason);
        return ResponseEntity.ok(Map.of(
                "transferId", transferId,
                "newStatus", "FAILED",
                "reviewedAt", Instant.now().toString()));
    }
}
