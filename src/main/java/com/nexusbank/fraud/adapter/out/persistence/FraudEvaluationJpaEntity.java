package com.nexusbank.fraud.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "fraud", name = "evaluations")
class FraudEvaluationJpaEntity {

    @Id
    UUID id;

    @Column(name = "transfer_id", nullable = false, length = 36)
    String transferId;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(nullable = false)
    Integer score;

    @Column(nullable = false, length = 20)
    String decision;

    /** Lista de nomes de regras serializada como JSON array. */
    @Column(name = "triggered_rules", nullable = false, columnDefinition = "TEXT")
    String triggeredRules;

    @Column(name = "evaluated_at", nullable = false)
    Instant evaluatedAt;

    protected FraudEvaluationJpaEntity() {}

    FraudEvaluationJpaEntity(UUID id, String transferId, String userId,
                             Integer score, String decision,
                             String triggeredRules, Instant evaluatedAt) {
        this.id = id;
        this.transferId = transferId;
        this.userId = userId;
        this.score = score;
        this.decision = decision;
        this.triggeredRules = triggeredRules;
        this.evaluatedAt = evaluatedAt;
    }
}
