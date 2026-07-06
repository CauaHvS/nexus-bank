package com.nexusbank.corebanking.domain.port.out;

/**
 * Representa uma mensagem de outbox não publicada, exposta pela porta de domínio do CoreBanking.
 */
public record OutboxMessage(Long id, String aggregateId, String eventType, String payload) {}
