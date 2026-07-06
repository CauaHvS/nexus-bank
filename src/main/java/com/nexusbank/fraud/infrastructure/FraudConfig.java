package com.nexusbank.fraud.infrastructure;

import com.nexusbank.fraud.application.usecase.EvaluateFraudUseCase;
import com.nexusbank.fraud.domain.port.out.FraudAuditRepository;
import com.nexusbank.fraud.domain.port.out.FraudContextRepository;
import com.nexusbank.fraud.domain.rule.FraudRule;
import com.nexusbank.fraud.domain.rule.HighFrequencyRule;
import com.nexusbank.fraud.domain.rule.HighValueRule;
import com.nexusbank.fraud.domain.rule.NewDestinationRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração do módulo Fraud: instancia use cases e registra regras de avaliação.
 *
 * EvaluateFraudUseCase usa FraudContextRepository (adapter JdbcTemplate interno ao Fraud)
 * para evitar dependência circular com PaymentsApi.
 *
 * Os endpoints de revisão manual (approve/reject) ficam no módulo Payments
 * (FraudReviewController) pois afetam diretamente o estado de transferências —
 * decisão que elimina a dependência bidirecional e o ciclo de módulo.
 * Ver ADR-011 para contexto.
 */
@Configuration
class FraudConfig {

    @Bean
    EvaluateFraudUseCase evaluateFraudUseCase(FraudAuditRepository auditRepository,
                                              FraudContextRepository contextRepository) {
        List<FraudRule> rules = List.of(
                new HighValueRule(),
                new HighFrequencyRule(),
                new NewDestinationRule()
        );
        return new EvaluateFraudUseCase(rules, auditRepository, contextRepository);
    }
}
