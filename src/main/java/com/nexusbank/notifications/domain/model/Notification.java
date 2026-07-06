package com.nexusbank.notifications.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate raiz do módulo Notifications.
 *
 * Invariantes:
 * - Uma Notification nasce sempre com read = false.
 * - markAsRead() é idempotente: chamar em notificação já lida não lança erro.
 * - notificationId é gerado no factory create(...) — nunca pelo banco.
 */
public class Notification {

    private final NotificationId id;
    private final String userId;
    private final NotificationType type;
    private final String title;
    private final String body;
    private boolean read;
    private final Instant createdAt;

    private Notification(NotificationId id, String userId, NotificationType type,
                         String title, String body, boolean read, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        this.userId = Objects.requireNonNull(userId, "userId não pode ser nulo");
        this.type = Objects.requireNonNull(type, "type não pode ser nulo");
        this.title = Objects.requireNonNull(title, "title não pode ser nulo");
        this.body = Objects.requireNonNull(body, "body não pode ser nulo");
        this.read = read;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt não pode ser nulo");
    }

    public static Notification create(String userId, NotificationType type, String title, String body) {
        return new Notification(
                NotificationId.generate(),
                userId,
                type,
                title,
                body,
                false,
                Instant.now());
    }

    public static Notification reconstitute(NotificationId id, String userId, NotificationType type,
                                            String title, String body, boolean read, Instant createdAt) {
        return new Notification(id, userId, type, title, body, read, createdAt);
    }

    public void markAsRead() {
        this.read = true;
    }

    public NotificationId getId()       { return id; }
    public String userId()              { return userId; }
    public NotificationType type()      { return type; }
    public String title()               { return title; }
    public String body()                { return body; }
    public boolean isRead()             { return read; }
    public Instant createdAt()          { return createdAt; }
}
