package com.nexusbank.notifications.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notifications")
public class NotificationJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    public UUID id;

    @Column(name = "user_id", nullable = false, length = 36)
    public String userId;

    @Column(nullable = false, length = 50)
    public String type;

    @Column(nullable = false, length = 255)
    public String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String body;

    @Column(nullable = false)
    public boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    protected NotificationJpaEntity() {}

    public NotificationJpaEntity(UUID id, String userId, String type, String title,
                                  String body, boolean read, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.read = read;
        this.createdAt = createdAt;
    }
}
