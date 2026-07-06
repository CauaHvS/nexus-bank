/**
 * Módulo Fraud — avaliação de risco e revisão manual de transferências.
 *
 * API pública exposta a outros módulos:
 *   - FraudApi: interface chamada pelo módulo Payments antes do débito.
 *   - FraudDecision: enum resultado da avaliacao (APPROVED, SUSPICIOUS, BLOCKED).
 *   - FraudEvaluationRequest: dados da transferência passados pelo Payments.
 *
 * Tudo nos subpacotes domain.*, application.*, adapter.* e infrastructure.*
 * é interno e não deve ser acessado por outros módulos.
 *
 * Dependência declarada:
 *   - payments: para chamar PaymentsApi nos casos de uso de revisão manual
 *     (approve/reject de transferências em UNDER_REVIEW).
 *
 * O Payments depende do Fraud (chama FraudApi), mas o Fraud também depende do
 * Payments (chama PaymentsApi para mudar status de revisão). Essa dependência
 * bidirecional é aceita e documentada em ADR-011. O Spring Modulith valida que
 * a comunicação ocorre apenas pelas interfaces públicas de cada módulo.
 *
 * Ver: docs/adr/ADR-011-fraud-payments-integration.md
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Fraud",
        allowedDependencies = {"payments"}
)
package com.nexusbank.fraud;
