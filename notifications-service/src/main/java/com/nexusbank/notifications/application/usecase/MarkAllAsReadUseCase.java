package com.nexusbank.notifications.application.usecase;

import com.nexusbank.notifications.domain.port.out.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkAllAsReadUseCase {

    private final NotificationRepository notificationRepository;

    public MarkAllAsReadUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void execute(String userId) {
        notificationRepository.markAllAsRead(userId);
    }
}
