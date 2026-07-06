package com.nexusbank.corebanking.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "outbox", schema = "corebanking")
public class CoreBankingOutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "aggregate_id", nullable = false)
    public String aggregateId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Column(name = "published", nullable = false)
    public boolean published = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "published_at")
    public Instant publishedAt;

    protected CoreBankingOutboxJpaEntity() {}

    public CoreBankingOutboxJpaEntity(String aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }
}
