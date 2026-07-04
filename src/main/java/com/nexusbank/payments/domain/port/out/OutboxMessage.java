package com.nexusbank.payments.domain.port.out;

/**
 * Representa uma mensagem de outbox não publicada, exposta pela porta de domínio.
 * Mantém o adaptador JPA encapsulado dentro do módulo payments.
 */
public record OutboxMessage(Long id, String aggregateId, String eventType, String payload) {}
