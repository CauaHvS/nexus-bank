package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;

/**
 * Dispara (+25) quando o destino nunca recebeu uma transferência COMPLETED do usuário.
 */
public class NewDestinationRule implements FraudRule {

    @Override
    public String name() {
        return "NewDestinationRule";
    }

    @Override
    public int score(FraudContext context) {
        return context.isNewDestination() ? 25 : 0;
    }
}
