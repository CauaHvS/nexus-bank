package com.nexusbank.notifications.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_dlq", schema = "notifications")
public class DlqJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    public UUID id;

    @Column(nullable = false, length = 255)
    public String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "retry_count", nullable = false)
    public int retryCount = 0;

    @Column(nullable = false)
    public boolean exhausted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "last_retry_at")
    public Instant lastRetryAt;

    protected DlqJpaEntity() {}

    public DlqJpaEntity(String topic, String payload, String errorMessage) {
        this.id = UUID.randomUUID();
        this.topic = topic;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.retryCount = 0;
        this.exhausted = false;
        this.createdAt = Instant.now();
    }
}
