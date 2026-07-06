package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;

import java.math.BigDecimal;

/**
 * Dispara (+40) quando o valor da transferência supera R$ 5.000,00.
 * Limiar exclusivo: exatamente 5000 não dispara.
 */
public class HighValueRule implements FraudRule {

    private static final BigDecimal THRESHOLD = new BigDecimal("5000");

    @Override
    public String name() {
        return "HighValueRule";
    }

    @Override
    public int score(FraudContext context) {
        return context.amount().compareTo(THRESHOLD) > 0 ? 40 : 0;
    }
}
