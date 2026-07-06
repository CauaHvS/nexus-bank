package com.nexusbank.notifications.domain.port.out;

import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.model.NotificationId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    Page<Notification> findByUserId(String userId, Pageable pageable);
    long countUnread(String userId);
    void markAllAsRead(String userId);
    Optional<Notification> findByIdAndUserId(NotificationId id, String userId);
}
