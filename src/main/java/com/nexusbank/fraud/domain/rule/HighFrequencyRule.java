package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;

/**
 * Dispara (+35) quando o usuário realizou 5 ou mais transferências nas últimas 24 horas.
 */
public class HighFrequencyRule implements FraudRule {

    private static final int THRESHOLD = 5;

    @Override
    public String name() {
        return "HighFrequencyRule";
    }

    @Override
    public int score(FraudContext context) {
        return context.transferCountLast24h() >= THRESHOLD ? 35 : 0;
    }
}
