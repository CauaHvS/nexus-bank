package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;

/**
 * Contrato para regras de avaliação de risco.
 * Cada regra retorna um score de contribuição (0 se não disparada, valor > 0 se disparada).
 */
public interface FraudRule {
    String name();
    int score(FraudContext context);
}
