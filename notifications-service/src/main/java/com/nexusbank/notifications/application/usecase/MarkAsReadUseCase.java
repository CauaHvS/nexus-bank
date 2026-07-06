package com.nexusbank.notifications.application.usecase;

import com.nexusbank.notifications.application.dto.NotificationView;
import com.nexusbank.notifications.domain.exception.NotificationNotFoundException;
import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.model.NotificationId;
import com.nexusbank.notifications.domain.port.out.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkAsReadUseCase {

    private final NotificationRepository notificationRepository;

    public MarkAsReadUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationView execute(String notificationId, String userId) {
        NotificationId id = NotificationId.of(notificationId);
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        notification.markAsRead();
        Notification saved = notificationRepository.save(notification);

        return new NotificationView(
                saved.getId().value().toString(),
                saved.userId(),
                saved.type().name(),
                saved.title(),
                saved.body(),
                saved.isRead(),
                saved.createdAt());
    }
}
