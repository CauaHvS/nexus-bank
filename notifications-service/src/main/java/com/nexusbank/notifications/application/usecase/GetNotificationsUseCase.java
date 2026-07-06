package com.nexusbank.notifications.application.usecase;

import com.nexusbank.notifications.application.dto.NotificationListResult;
import com.nexusbank.notifications.application.dto.NotificationView;
import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.port.out.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetNotificationsUseCase {

    private final NotificationRepository notificationRepository;

    public GetNotificationsUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationListResult execute(String userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> resultPage = notificationRepository.findByUserId(userId, pageable);
        long unreadCount = notificationRepository.countUnread(userId);

        return new NotificationListResult(
                resultPage.getContent().stream().map(this::toView).toList(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                unreadCount);
    }

    @Transactional(readOnly = true)
    public long countUnread(String userId) {
        return notificationRepository.countUnread(userId);
    }

    private NotificationView toView(Notification n) {
        return new NotificationView(
                n.getId().value().toString(),
                n.userId(),
                n.type().name(),
                n.title(),
                n.body(),
                n.isRead(),
                n.createdAt());
    }
}
