# Plano de Implementação — Módulo Fraud (Fatia 5.1)

## Decisão de Integração: Opção C — Síncrona in-process via FraudApi

### Por que não Opção B (Kafka assíncrono)

O `InitiateTransferUseCase` debita a conta de origem *dentro da mesma transação*
em que persiste a transferência. O evento `TransferInitiated` só é escrito no
Outbox após o débito. Interpor fraud *depois* do Outbox significaria que o dinheiro
já saiu da conta antes da avaliação — comportamento inaceitável para bloqueio.

Inverter o fluxo (publicar evento antes do débito, esperar resposta assíncrona)
exigiria um mecanismo de correlação com timeout, saga aninhada e estado transitório
adicional. Custo alto, nenhum benefício real num monólito onde os dois módulos
rodam no mesmo processo.

### Por que Opção C

O Spring Modulith permite chamada direta entre módulos desde que seja pela interface
pública (`FraudApi` no pacote raiz). O Fraud não acessa internals do Payments, e o
Payments não acessa internals do Fraud. A fronteira de módulo é respeitada.

A avaliação é síncrona e ocorre *antes* do débito. Isso permite:
- BLOCKED: lança `FraudBlockedException`, transação reverte, débito nunca ocorre.
- SUSPICIOUS: persiste transferência com status `UNDER_REVIEW`, sem debitar.
- APPROVED: fluxo normal segue.

### Impacto nos testes

- Testes unitários do domínio Fraud são isolados (sem Spring, sem banco).
- Testes de integração do `InitiateTransferUseCase` precisam mockar `FraudApi`
  (ou usar uma implementação stub) — mesma mecânica já usada com `CoreBankingApi`.
- O `ApplicationModules.verify()` já valida que o Payments depende do Fraud apenas
  pela interface pública.

### Impacto na futura extração

Se o Fraud for extraído para microsserviço, a chamada síncrona vira HTTP/gRPC com
timeout. A `FraudApi` continua sendo a interface, mas a implementação passa a ser
um cliente HTTP. O custo de extração é localizado: apenas o adapter de saída do
Fraud muda dentro do módulo Payments. O domínio de ambos os módulos não é tocado.

Ver decisão completa em: `docs/adr/ADR-011-fraud-payments-integration.md`

---

## Pré-requisito: adicionar UNDER_REVIEW ao TransferStatus

**Arquivo:** `src/main/java/com/nexusbank/payments/domain/model/TransferStatus.java`

Adicionar `UNDER_REVIEW` ao enum existente:

```
SCHEDULED, PENDING, UNDER_REVIEW, COMPLETED, FAILED, COMPENSATION_FAILED
```

**Máquina de estados ampliada:**
```
SCHEDULED    -> PENDING       (job de agendamento via activate())
PENDING      -> COMPLETED     (CompleteTransferUseCase)
PENDING      -> FAILED        (compensação)
PENDING      -> UNDER_REVIEW  (Fraud decide SUSPICIOUS — sem débito)
UNDER_REVIEW -> PENDING       (ApproveFraudReviewUseCase — débita e aciona crédito)
UNDER_REVIEW -> FAILED        (RejectFraudReviewUseCase)
```

Adicionar métodos no agregado `Transfer`:

```java
public void markUnderReview() {
    if (this.status != TransferStatus.PENDING)
        throw new IllegalStateException("Apenas PENDING pode ir para UNDER_REVIEW");
    this.status = TransferStatus.UNDER_REVIEW;
}

public boolean isUnderReview() {
    return status == TransferStatus.UNDER_REVIEW;
}
```

**Nota:** O `Transfer.initiate()` cria com status PENDING. O `InitiateTransferUseCase`
chama `FraudApi.evaluate()` antes do débito e, se decisão for SUSPICIOUS, chama
`transfer.markUnderReview()` e persiste sem debitar.

---

## Passo a passo de implementação

### Passo 1 — Contratos públicos do módulo Fraud

**Arquivo:** `src/main/java/com/nexusbank/fraud/FraudApi.java`

```java
package com.nexusbank.fraud;

public interface FraudApi {
    FraudDecision evaluate(FraudEvaluationRequest request);
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/FraudDecision.java`

```java
package com.nexusbank.fraud;

public enum FraudDecision {
    APPROVED,
    SUSPICIOUS,
    BLOCKED
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/FraudEvaluationRequest.java`

```java
package com.nexusbank.fraud;

import java.math.BigDecimal;

public record FraudEvaluationRequest(
    String transferId,
    String userId,
    String sourceAccountId,
    String targetAccountId,
    BigDecimal amount,
    String paymentType
) {}
```

---

### Passo 2 — Domínio puro (sem Spring, sem JPA)

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/model/FraudContext.java`

```java
package com.nexusbank.fraud.domain.model;

import java.math.BigDecimal;

public record FraudContext(
    String transferId,
    String userId,
    String sourceAccountId,
    String targetAccountId,
    BigDecimal amount,
    String paymentType,
    int transferCountLast24h,
    boolean isNewDestination
) {}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/model/FraudEvaluation.java`

```java
package com.nexusbank.fraud.domain.model;

import com.nexusbank.fraud.FraudDecision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class FraudEvaluation {

    private final UUID evaluationId;
    private final String transferId;
    private final String userId;
    private final int score;
    private final FraudDecision decision;
    private final List<String> triggeredRules;
    private final Instant evaluatedAt;

    private FraudEvaluation(UUID evaluationId, String transferId, String userId,
                            int score, FraudDecision decision, List<String> triggeredRules,
                            Instant evaluatedAt) {
        this.evaluationId = evaluationId;
        this.transferId = transferId;
        this.userId = userId;
        this.score = score;
        this.decision = decision;
        this.triggeredRules = List.copyOf(triggeredRules);
        this.evaluatedAt = evaluatedAt;
    }

    public static FraudEvaluation evaluate(String transferId, String userId,
                                           int score, List<String> triggeredRules) {
        FraudDecision decision;
        if (score >= 70)      decision = FraudDecision.BLOCKED;
        else if (score >= 30) decision = FraudDecision.SUSPICIOUS;
        else                  decision = FraudDecision.APPROVED;

        return new FraudEvaluation(UUID.randomUUID(), transferId, userId,
                score, decision, triggeredRules, Instant.now());
    }

    // reconstitui do banco sem gerar novo UUID
    public static FraudEvaluation reconstitute(UUID evaluationId, String transferId,
                                               String userId, int score, FraudDecision decision,
                                               List<String> triggeredRules, Instant evaluatedAt) {
        return new FraudEvaluation(evaluationId, transferId, userId, score,
                decision, triggeredRules, evaluatedAt);
    }

    public UUID getEvaluationId()           { return evaluationId; }
    public String getTransferId()           { return transferId; }
    public String getUserId()               { return userId; }
    public int getScore()                   { return score; }
    public FraudDecision getDecision()      { return decision; }
    public List<String> getTriggeredRules() { return triggeredRules; }
    public Instant getEvaluatedAt()         { return evaluatedAt; }
}
```

---

### Passo 3 — Regras de Fraud

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/rule/FraudRule.java`

```java
package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;

public interface FraudRule {
    String name();
    int score(FraudContext context);
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/rule/HighValueRule.java`

```java
package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;

import java.math.BigDecimal;

public class HighValueRule implements FraudRule {

    private static final BigDecimal THRESHOLD = new BigDecimal("5000");

    @Override
    public String name() { return "HighValueRule"; }

    @Override
    public int score(FraudContext context) {
        return context.amount().compareTo(THRESHOLD) > 0 ? 40 : 0;
    }
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/rule/HighFrequencyRule.java`

```java
package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;

public class HighFrequencyRule implements FraudRule {

    private static final int THRESHOLD = 5;

    @Override
    public String name() { return "HighFrequencyRule"; }

    @Override
    public int score(FraudContext context) {
        return context.transferCountLast24h() >= THRESHOLD ? 35 : 0;
    }
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/rule/NewDestinationRule.java`

```java
package com.nexusbank.fraud.domain.rule;

import com.nexusbank.fraud.domain.model.FraudContext;

public class NewDestinationRule implements FraudRule {

    @Override
    public String name() { return "NewDestinationRule"; }

    @Override
    public int score(FraudContext context) {
        return context.isNewDestination() ? 25 : 0;
    }
}
```

---

### Passo 4 — Portas de saída do domínio

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/port/out/FraudAuditRepository.java`

```java
package com.nexusbank.fraud.domain.port.out;

import com.nexusbank.fraud.domain.model.FraudEvaluation;

import java.util.Optional;

public interface FraudAuditRepository {
    void save(FraudEvaluation evaluation);
    Optional<FraudEvaluation> findByTransferId(String transferId);
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/port/out/FraudContextRepository.java`

```java
package com.nexusbank.fraud.domain.port.out;

public interface FraudContextRepository {
    /**
     * Conta quantas transferências o userId realizou nas últimas 24 horas.
     * Consulta em payments.transfers.
     */
    int countTransfersLast24h(String userId);

    /**
     * Retorna true se o targetAccountId nunca recebeu uma transferência
     * com status COMPLETED do userId.
     * Consulta em payments.transfers.
     */
    boolean isNewDestination(String userId, String targetAccountId);
}
```

---

### Passo 5 — Exceções de domínio

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/exception/FraudBlockedException.java`

```java
package com.nexusbank.fraud.domain.exception;

public class FraudBlockedException extends RuntimeException {

    private final String transferId;
    private final int score;

    public FraudBlockedException(String transferId, int score) {
        super("Transferência bloqueada por fraude: id=" + transferId + " score=" + score);
        this.transferId = transferId;
        this.score = score;
    }

    public String getTransferId() { return transferId; }
    public int getScore()         { return score; }
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/domain/exception/TransferNotUnderReviewException.java`

```java
package com.nexusbank.fraud.domain.exception;

public class TransferNotUnderReviewException extends RuntimeException {

    private final String transferId;
    private final String currentStatus;

    public TransferNotUnderReviewException(String transferId, String currentStatus) {
        super("Transferência " + transferId + " não está em UNDER_REVIEW. Status atual: " + currentStatus);
        this.transferId = transferId;
        this.currentStatus = currentStatus;
    }

    public String getTransferId()    { return transferId; }
    public String getCurrentStatus() { return currentStatus; }
}
```

---

### Passo 6 — Casos de uso da camada application

**Arquivo:** `src/main/java/com/nexusbank/fraud/application/usecase/EvaluateFraudUseCase.java`

```java
package com.nexusbank.fraud.application.usecase;

import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.FraudEvaluationRequest;
import com.nexusbank.fraud.domain.exception.FraudBlockedException;
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
 * Orquestra a avaliação de risco: carrega contexto, aplica regras, persiste auditoria.
 * Lança FraudBlockedException se score >= 70.
 * Retorna FraudDecision para que o chamador (Payments) decida o que fazer com SUSPICIOUS.
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
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/application/usecase/ApproveFraudReviewUseCase.java`

```java
package com.nexusbank.fraud.application.usecase;

import com.nexusbank.payments.PaymentsApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Move transferência de UNDER_REVIEW para PENDING, executa débito e aciona crédito.
 * Depende de PaymentsApi (porta pública do módulo Payments).
 */
public class ApproveFraudReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveFraudReviewUseCase.class);

    private final PaymentsApi paymentsApi;

    public ApproveFraudReviewUseCase(PaymentsApi paymentsApi) {
        this.paymentsApi = paymentsApi;
    }

    public void execute(String transferId) {
        log.info("Aprovando revisao de fraude: transferId={}", transferId);
        paymentsApi.approveUnderReview(transferId);
    }
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/application/usecase/RejectFraudReviewUseCase.java`

```java
package com.nexusbank.fraud.application.usecase;

import com.nexusbank.payments.PaymentsApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Move transferência de UNDER_REVIEW para FAILED sem debitar.
 */
public class RejectFraudReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(RejectFraudReviewUseCase.class);

    private final PaymentsApi paymentsApi;

    public RejectFraudReviewUseCase(PaymentsApi paymentsApi) {
        this.paymentsApi = paymentsApi;
    }

    public void execute(String transferId, String reason) {
        log.info("Rejeitando revisao de fraude: transferId={} motivo={}", transferId, reason);
        paymentsApi.rejectUnderReview(transferId, reason);
    }
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/application/dto/FraudEvaluationView.java`

```java
package com.nexusbank.fraud.application.dto;

import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.domain.model.FraudEvaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FraudEvaluationView(
    UUID evaluationId,
    String transferId,
    String userId,
    int score,
    FraudDecision decision,
    List<String> triggeredRules,
    Instant evaluatedAt
) {
    public static FraudEvaluationView from(FraudEvaluation evaluation) {
        return new FraudEvaluationView(
                evaluation.getEvaluationId(),
                evaluation.getTransferId(),
                evaluation.getUserId(),
                evaluation.getScore(),
                evaluation.getDecision(),
                evaluation.getTriggeredRules(),
                evaluation.getEvaluatedAt()
        );
    }
}
```

---

### Passo 7 — PaymentsApi (porta pública que o Fraud precisa)

O `ApproveFraudReviewUseCase` e `RejectFraudReviewUseCase` precisam mudar o estado
de uma transferência no módulo Payments. Isso exige uma `PaymentsApi` pública,
análoga à `CoreBankingApi`.

**Arquivo a criar:** `src/main/java/com/nexusbank/payments/PaymentsApi.java`

```java
package com.nexusbank.payments;

/**
 * API pública do módulo Payments.
 * Único ponto de entrada para outros módulos alterarem estado de transferências.
 */
public interface PaymentsApi {
    /**
     * Aprova uma transferência em UNDER_REVIEW: move para PENDING, executa débito
     * e aciona o fluxo de crédito (CompleteTransferUseCase).
     * Lança TransferNotFoundException se transferId não existir.
     * Lança TransferNotUnderReviewException se status atual não for UNDER_REVIEW.
     */
    void approveUnderReview(String transferId);

    /**
     * Rejeita uma transferência em UNDER_REVIEW: move para FAILED sem debitar.
     * Lança TransferNotFoundException se transferId não existir.
     * Lança TransferNotUnderReviewException se status atual não for UNDER_REVIEW.
     */
    void rejectUnderReview(String transferId, String reason);
}
```

**Implementação a criar:** `src/main/java/com/nexusbank/payments/infrastructure/PaymentsService.java`

Anota com `@Service`, implementa `PaymentsApi`. O backend-engineer deve criar um
`ApproveTransferUseCase` dedicado em vez de reutilizar o `InitiateTransferUseCase`
(responsabilidades distintas). A lógica de `approveUnderReview`:

1. Carregar transferência pelo ID
2. Validar que status == UNDER_REVIEW (lança `TransferNotUnderReviewException` se não)
3. Mover para PENDING (`transfer.approveFraudReview()` — novo método no agregado)
4. Debitar conta de origem via `CoreBankingApi.debit()`
5. Persistir e acionar `CompleteTransferUseCase`

---

### Passo 8 — Adapters de persistência

**Migration SQL:**

Criar arquivo: `src/main/resources/db/migration/fraud/V1__init_fraud_schema.sql`

```sql
CREATE SCHEMA IF NOT EXISTS fraud;

CREATE TABLE fraud.evaluations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id     VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    score           INTEGER     NOT NULL,
    decision        VARCHAR(20) NOT NULL,
    triggered_rules TEXT        NOT NULL,
    evaluated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_evaluations_transfer ON fraud.evaluations(transfer_id);
CREATE INDEX idx_fraud_evaluations_user     ON fraud.evaluations(user_id, evaluated_at DESC);
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/adapter/out/persistence/FraudEvaluationJpaEntity.java`

```java
package com.nexusbank.fraud.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "fraud", name = "evaluations")
public class FraudEvaluationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "transfer_id", nullable = false, length = 36)
    private String transferId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(name = "triggered_rules", nullable = false, columnDefinition = "TEXT")
    private String triggeredRules; // JSON array serializado como string

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    // construtores, getters e setters seguindo o padrão já estabelecido no projeto
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/adapter/out/persistence/FraudEvaluationJpaRepository.java`

```java
package com.nexusbank.fraud.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FraudEvaluationJpaRepository extends JpaRepository<FraudEvaluationJpaEntity, UUID> {
    Optional<FraudEvaluationJpaEntity> findByTransferId(String transferId);
}
```

**`FraudAuditPersistenceAdapter`** — implementa `FraudAuditRepository`. Usa `ObjectMapper`
para serializar `triggeredRules` como JSON array (`List<String>` -> `TEXT`) e
desserializar na reconstituição.

**`FraudContextPersistenceAdapter`** — implementa `FraudContextRepository`. Usa
`JdbcTemplate` consultando `payments.transfers` diretamente:

```sql
-- countTransfersLast24h
SELECT COUNT(*) FROM payments.transfers
WHERE source_account_id IN (
    SELECT id FROM corebanking.accounts WHERE customer_id = :userId
)
AND created_at >= NOW() - INTERVAL '24 hours'
AND status NOT IN ('FAILED', 'COMPENSATION_FAILED');

-- isNewDestination: retorna true se COUNT = 0
SELECT COUNT(*) FROM payments.transfers
WHERE source_account_id IN (
    SELECT id FROM corebanking.accounts WHERE customer_id = :userId
)
AND target_account_id = :targetAccountId
AND status = 'COMPLETED';
```

**Nota arquitetural:** A leitura cruzada de schema (`payments.transfers` lido pelo
Fraud) é aceitável no monólito. Na extração do Fraud para microsserviço, esse adapter
viraria uma chamada à `PaymentsApi` REST. O isolamento está no código, não no banco.

**Atenção para o backend-engineer:** verifique a estrutura real da tabela
`payments.transfers` para confirmar o nome da coluna que identifica o usuario
(`source_account_id` referencia a conta, não diretamente o `user_id`). Ajuste a
query conforme o schema existente.

---

### Passo 9 — Adapter web (controller)

**Arquivo:** `src/main/java/com/nexusbank/fraud/adapter/in/web/FraudController.java`

```java
package com.nexusbank.fraud.adapter.in.web;

import com.nexusbank.fraud.application.dto.FraudEvaluationView;
import com.nexusbank.fraud.application.usecase.ApproveFraudReviewUseCase;
import com.nexusbank.fraud.application.usecase.RejectFraudReviewUseCase;
import com.nexusbank.fraud.domain.port.out.FraudAuditRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fraud")
public class FraudController {

    private final FraudAuditRepository auditRepository;
    private final ApproveFraudReviewUseCase approveUseCase;
    private final RejectFraudReviewUseCase rejectUseCase;

    public FraudController(FraudAuditRepository auditRepository,
                           ApproveFraudReviewUseCase approveUseCase,
                           RejectFraudReviewUseCase rejectUseCase) {
        this.auditRepository = auditRepository;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
    }

    @GetMapping("/evaluations/{transferId}")
    public ResponseEntity<FraudEvaluationView> getEvaluation(@PathVariable String transferId) {
        return auditRepository.findByTransferId(transferId)
                .map(FraudEvaluationView::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reviews/{transferId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String transferId) {
        approveUseCase.execute(transferId);
        return ResponseEntity.ok(Map.of(
                "transferId", transferId,
                "newStatus", "PENDING",
                "reviewedAt", Instant.now().toString()));
    }

    @PostMapping("/reviews/{transferId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reject(
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
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/adapter/in/web/RejectReviewRequest.java`

```java
package com.nexusbank.fraud.adapter.in.web;

import jakarta.validation.constraints.Size;

public record RejectReviewRequest(
    @Size(max = 500) String reason
) {}
```

---

### Passo 10 — Infrastructure (bean configuration)

**Arquivo:** `src/main/java/com/nexusbank/fraud/infrastructure/FraudService.java`

```java
package com.nexusbank.fraud.infrastructure;

import com.nexusbank.fraud.FraudApi;
import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.FraudEvaluationRequest;
import com.nexusbank.fraud.application.usecase.EvaluateFraudUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementacao de FraudApi. Participa da transacao do chamador (Payments).
 * A auditoria é persistida atomicamente com a decisão de UNDER_REVIEW ou BLOCKED.
 */
@Service
public class FraudService implements FraudApi {

    private final EvaluateFraudUseCase evaluateUseCase;

    public FraudService(EvaluateFraudUseCase evaluateUseCase) {
        this.evaluateUseCase = evaluateUseCase;
    }

    @Override
    @Transactional
    public FraudDecision evaluate(FraudEvaluationRequest request) {
        return evaluateUseCase.execute(request);
    }
}
```

**Arquivo:** `src/main/java/com/nexusbank/fraud/infrastructure/FraudConfig.java`

```java
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

@Configuration
public class FraudConfig {

    @Bean
    public EvaluateFraudUseCase evaluateFraudUseCase(
            FraudAuditRepository auditRepository,
            FraudContextRepository contextRepository) {
        List<FraudRule> rules = List.of(
                new HighValueRule(),
                new HighFrequencyRule(),
                new NewDestinationRule()
        );
        return new EvaluateFraudUseCase(rules, auditRepository, contextRepository);
    }
}
```

---

### Passo 11 — Modificar InitiateTransferUseCase (módulo Payments)

Injetar `FraudApi` no construtor e inserir a avaliação após a validação de ownership,
antes do débito:

```java
// após coreBankingApi.isOwner(...)
// antes do coreBankingApi.debit(...)

var fraudRequest = new FraudEvaluationRequest(
        transfer.getId().toString(),
        command.authenticatedUserId(),
        command.sourceAccountId(),
        command.targetAccountId(),
        command.amount(),
        command.type()
);

FraudDecision fraudDecision = fraudApi.evaluate(fraudRequest);
// FraudBlockedException é lançada internamente pelo use case se BLOCKED

if (fraudDecision == FraudDecision.SUSPICIOUS) {
    transfer.markUnderReview();
    transferRepository.save(transfer);
    log.info("Transferência marcada como UNDER_REVIEW por suspeita de fraude: id={}", transfer.getId());
    return TransferResult.from(transfer);
}
// APPROVED: segue para débito normalmente
```

**Importante:** A `FraudBlockedException` deve ser mapeada no `GlobalExceptionHandler`
para HTTP 422 (Unprocessable Entity). Não é 400 (input inválido) nem 500 (erro
interno): é uma regra de negócio que impediu o processamento.

---

### Passo 12 — GlobalExceptionHandler: novos mapeamentos

Em `src/main/java/com/nexusbank/infrastructure/web/GlobalExceptionHandler.java`,
adicionar:

```java
@ExceptionHandler(FraudBlockedException.class)
public ProblemDetail handleFraudBlocked(FraudBlockedException ex) {
    var problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Transferência bloqueada por análise de risco. Score: " + ex.getScore());
    problem.setType(URI.create("https://nexusbank.com/errors/fraud-blocked"));
    problem.setTitle("Transferência bloqueada por fraude");
    problem.setProperty("transferId", ex.getTransferId());
    problem.setProperty("score", ex.getScore());
    return problem;
}

@ExceptionHandler(TransferNotUnderReviewException.class)
public ProblemDetail handleNotUnderReview(TransferNotUnderReviewException ex) {
    var problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage());
    problem.setType(URI.create("https://nexusbank.com/errors/transfer-not-under-review"));
    problem.setTitle("Transferência não está em revisão");
    problem.setProperty("transferId", ex.getTransferId());
    problem.setProperty("currentStatus", ex.getCurrentStatus());
    return problem;
}
```

---

## Estrutura final de pacotes

```
fraud/
  FraudApi.java                                    <- interface pública
  FraudDecision.java                               <- enum público
  FraudEvaluationRequest.java                      <- record público
  package-info.java                                <- @ApplicationModule(allowedDependencies=payments)
  domain/
    model/
      FraudContext.java
      FraudEvaluation.java
    rule/
      FraudRule.java
      HighValueRule.java
      HighFrequencyRule.java
      NewDestinationRule.java
    port/
      out/
        FraudAuditRepository.java
        FraudContextRepository.java
    exception/
      FraudBlockedException.java
      TransferNotUnderReviewException.java
  application/
    usecase/
      EvaluateFraudUseCase.java
      ApproveFraudReviewUseCase.java
      RejectFraudReviewUseCase.java
    dto/
      FraudEvaluationView.java
  adapter/
    in/
      web/
        FraudController.java
        RejectReviewRequest.java
    out/
      persistence/
        FraudEvaluationJpaEntity.java
        FraudEvaluationJpaRepository.java
        FraudAuditPersistenceAdapter.java
        FraudContextPersistenceAdapter.java
  infrastructure/
    FraudService.java                              <- implementa FraudApi
    FraudConfig.java                               <- @Configuration, instancia use cases

payments/ (modificacoes)
  PaymentsApi.java                                 <- nova interface pública
  infrastructure/
    PaymentsService.java                           <- implementa PaymentsApi
  application/
    usecase/
      InitiateTransferUseCase.java                 <- injeta FraudApi
      ApproveTransferUseCase.java                  <- novo: lógica de UNDER_REVIEW -> débito
  domain/
    model/
      TransferStatus.java                          <- adiciona UNDER_REVIEW
      Transfer.java                                <- adiciona markUnderReview(), isUnderReview()
```

---

## Ordem de implementação recomendada

1. Adicionar `UNDER_REVIEW` em `TransferStatus` e métodos no agregado `Transfer`.
2. Criar `PaymentsApi` e implementar `PaymentsService` + `ApproveTransferUseCase`.
3. Implementar contratos públicos do Fraud (`FraudApi`, `FraudDecision`, `FraudEvaluationRequest`).
4. Implementar domínio Fraud (sem Spring): `FraudContext`, `FraudEvaluation`, regras, portas, exceções.
5. Implementar `EvaluateFraudUseCase` (puro, sem anotacoes Spring).
6. Implementar `ApproveFraudReviewUseCase` e `RejectFraudReviewUseCase`.
7. Criar migration `V1__init_fraud_schema.sql`.
8. Implementar adapters de persistência (`FraudAuditPersistenceAdapter`, `FraudContextPersistenceAdapter`).
9. Configurar `FraudConfig` e `FraudService`.
10. Modificar `InitiateTransferUseCase` para injetar e chamar `FraudApi`.
11. Adicionar mapeamentos de exceção no `GlobalExceptionHandler`.
12. Implementar `FraudController`.
13. Validar `ApplicationModules.verify()` ainda passa.

---

## Contrato de teste (para o test-engineer)

### Testes unitários do domínio (sem Spring)

- `FraudEvaluationTest`: score < 30 -> APPROVED; 30-69 -> SUSPICIOUS; >= 70 -> BLOCKED.
- `HighValueRuleTest`: amount = 5000 nao dispara (boundary exato); amount = 5001 dispara (+40).
- `HighFrequencyRuleTest`: 4 transferências nao dispara; 5 dispara (+35).
- `NewDestinationRuleTest`: isNewDestination = false nao dispara; true dispara (+25).
- `EvaluateFraudUseCaseTest`: mock de `FraudAuditRepository` e `FraudContextRepository`;
  combinacao de regras; verifica que `auditRepository.save()` é sempre chamado;
  verifica que `FraudBlockedException` é lancada quando score >= 70.

### Testes de integração (Testcontainers)

- `FraudSuspiciousIntegrationTest`: transferência de valor alto (>5000) com destino novo
  -> HTTP 200, status = UNDER_REVIEW, saldo da origem inalterado.
- `FraudBlockedIntegrationTest`: valor alto + alta frequência + destino novo (score = 100)
  -> HTTP 422, saldo da origem inalterado, avaliação auditada em `fraud.evaluations`.
- `ApproveFraudReviewIntegrationTest`: aprovar UNDER_REVIEW -> transferência COMPLETED,
  saldo debitado e creditado corretamente.
- `RejectFraudReviewIntegrationTest`: rejeitar UNDER_REVIEW -> status FAILED, saldo intacto.
- `FraudAuditIntegrationTest`: toda avaliação (APPROVED, SUSPICIOUS, BLOCKED) persiste
  registro em `fraud.evaluations`.
- `FraudApproveIdempotencyTest`: chamar `/approve` duas vezes com o mesmo `transferId`
  deve resultar no mesmo estado sem débito duplo (segunda chamada recebe 409).
