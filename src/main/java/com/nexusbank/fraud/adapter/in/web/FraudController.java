package com.nexusbank.fraud.adapter.in.web;

import com.nexusbank.fraud.application.dto.FraudEvaluationView;
import com.nexusbank.fraud.domain.port.out.FraudAuditRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Expõe consultas de avaliação de risco do módulo Fraud.
 *
 * Nota: os endpoints de revisão manual (/fraud/reviews/) ficam em
 * FraudReviewController dentro do módulo Payments, pois alteram estado
 * de transferências e precisam de ApproveUnderReviewUseCase e
 * RejectUnderReviewUseCase. Essa separação elimina o ciclo de módulo
 * entre Fraud e Payments. Ver ADR-011.
 */
@RestController
@RequestMapping("/fraud")
class FraudController {

    private final FraudAuditRepository auditRepository;

    FraudController(FraudAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping("/evaluations/{transferId}")
    ResponseEntity<FraudEvaluationView> getEvaluation(@PathVariable String transferId) {
        return auditRepository.findByTransferId(transferId)
                .map(FraudEvaluationView::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
