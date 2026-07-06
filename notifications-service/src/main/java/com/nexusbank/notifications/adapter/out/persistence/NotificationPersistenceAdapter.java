package com.nexusbank.notifications.adapter.out.persistence;

import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.model.NotificationId;
import com.nexusbank.notifications.domain.model.NotificationType;
import com.nexusbank.notifications.domain.port.out.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class NotificationPersistenceAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    NotificationPersistenceAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = toEntity(notification);
        NotificationJpaEntity saved = jpa.save(entity);
        return toDomain(saved);
    }

    @Override
    public Page<Notification> findByUserId(String userId, Pageable pageable) {
        return jpa.findByUserId(userId, pageable).map(this::toDomain);
    }

    @Override
    public long countUnread(String userId) {
        return jpa.countUnreadByUserId(userId);
    }

    @Override
    public void markAllAsRead(String userId) {
        jpa.markAllAsReadByUserId(userId);
    }

    @Override
    public Optional<Notification> findByIdAndUserId(NotificationId id, String userId) {
        return jpa.findByIdAndUserId(id.value(), userId).map(this::toDomain);
    }

    private NotificationJpaEntity toEntity(Notification n) {
        return new NotificationJpaEntity(
                n.getId().value(),
                n.userId(),
                n.type().name(),
                n.title(),
                n.body(),
                n.isRead(),
                n.createdAt());
    }

    private Notification toDomain(NotificationJpaEntity e) {
        return Notification.reconstitute(
                NotificationId.of(e.id),
                e.userId,
                NotificationType.valueOf(e.type),
                e.title,
                e.body,
                e.read,
                e.createdAt);
    }
}
