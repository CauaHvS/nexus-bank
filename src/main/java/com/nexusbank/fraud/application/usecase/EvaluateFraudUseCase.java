package com.nexusbank.fraud.application.usecase;

import com.nexusbank.fraud.FraudBlockedException;
import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.FraudEvaluationRequest;
import com.nexusbank.fraud.domain.model.FraudContext;
import com.nexusbank.fraud.domain.model.FraudEvaluation;
import com.nexusbank.fraud.domain.port.out.FraudAuditRepository;
import com.nexusbank.fraud.domain.port.out.FraudContextRepository;
import com.nexusbank.fraud.domain.rule.FraudRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra a avaliação de risco: carrega contexto via FraudContextRepository,
 * aplica regras, persiste auditoria e retorna a decisão.
 * Lança FraudBlockedException se score >= 70 (decisão BLOCKED).
 * Usa FraudContextRepository (não PaymentsApi) para evitar ciclo de módulo.
 */
public class EvaluateFraudUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluateFraudUseCase.class);

    private final List<FraudRule> rules;
    private final FraudAuditRepository auditRepository;
    private final FraudContextRepository contextRepository;

    public EvaluateFraudUseCase(List<FraudRule> rules,
                                FraudAuditRepository auditRepository,
                                FraudContextRepository contextRepository) {
        this.rules = List.copyOf(rules);
        this.auditRepository = auditRepository;
        this.contextRepository = contextRepository;
    }

    public FraudDecision execute(FraudEvaluationRequest request) {
        int transferCountLast24h = contextRepository.countTransfersLast24h(request.userId());
        boolean isNewDestination = contextRepository.isNewDestination(
                request.userId(), request.targetAccountId());

        FraudContext context = new FraudContext(
                request.transferId(),
                request.userId(),
                request.sourceAccountId(),
                request.targetAccountId(),
                request.amount(),
                request.paymentType(),
                transferCountLast24h,
                isNewDestination
        );

        int totalScore = 0;
        List<String> triggeredRules = new ArrayList<>();
        for (FraudRule rule : rules) {
            int ruleScore = rule.score(context);
            if (ruleScore > 0) {
                totalScore += ruleScore;
                triggeredRules.add(rule.name());
            }
        }

        FraudEvaluation evaluation = FraudEvaluation.evaluate(
                request.transferId(), request.userId(), totalScore, triggeredRules);

        auditRepository.save(evaluation);

        log.info("Avaliacao de fraude: transferId={} score={} decisao={} regras={}",
                request.transferId(), totalScore, evaluation.getDecision(), triggeredRules);

        if (evaluation.getDecision() == FraudDecision.BLOCKED) {
            throw new FraudBlockedException(request.transferId(), totalScore);
        }

        return evaluation.getDecision();
    }
}
