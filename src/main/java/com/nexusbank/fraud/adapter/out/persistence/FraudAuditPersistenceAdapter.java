package com.nexusbank.fraud.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.fraud.domain.model.FraudEvaluation;
import com.nexusbank.fraud.domain.port.out.FraudAuditRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class FraudAuditPersistenceAdapter implements FraudAuditRepository {

    private final FraudEvaluationJpaRepository jpa;
    private final ObjectMapper objectMapper;

    FraudAuditPersistenceAdapter(FraudEvaluationJpaRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(FraudEvaluation evaluation) {
        String rulesJson = serializeRules(evaluation.getTriggeredRules());
        FraudEvaluationJpaEntity entity = new FraudEvaluationJpaEntity(
                evaluation.getEvaluationId(),
                evaluation.getTransferId(),
                evaluation.getUserId(),
                evaluation.getScore(),
                evaluation.getDecision().name(),
                rulesJson,
                evaluation.getEvaluatedAt()
        );
        jpa.save(entity);
    }

    @Override
    public Optional<FraudEvaluation> findByTransferId(String transferId) {
        return jpa.findByTransferId(transferId).map(this::toDomain);
    }

    private FraudEvaluation toDomain(FraudEvaluationJpaEntity e) {
        List<String> rules = deserializeRules(e.triggeredRules);
        return FraudEvaluation.reconstitute(
                e.id,
                e.transferId,
                e.userId,
                e.score,
                FraudDecision.valueOf(e.decision),
                rules,
                e.evaluatedAt
        );
    }

    private String serializeRules(List<String> rules) {
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Falha ao serializar regras de fraude", e);
        }
    }

    private List<String> deserializeRules(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Falha ao desserializar regras de fraude", e);
        }
    }
}
