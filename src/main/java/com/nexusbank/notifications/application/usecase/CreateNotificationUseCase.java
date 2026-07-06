package com.nexusbank.notifications.application.usecase;

import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.model.NotificationType;
import com.nexusbank.notifications.domain.port.out.NotificationRepository;
import com.nexusbank.notifications.domain.port.out.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateNotificationUseCase.class);

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;

    public CreateNotificationUseCase(NotificationRepository notificationRepository,
                                     NotificationSender notificationSender) {
        this.notificationRepository = notificationRepository;
        this.notificationSender = notificationSender;
    }

    @Transactional
    public void execute(String userId, NotificationType type, String title, String body) {
        Notification notification = Notification.create(userId, type, title, body);
        notificationRepository.save(notification);
        try {
            notificationSender.send(notification);
        } catch (Exception e) {
            log.warn("Falha ao enviar notificação externa para usuário {}: {}", userId, e.getMessage());
            // Não relança — falha no envio externo não deve reverter a persistência
        }
    }
}
