package com.nexusbank.notifications.adapter.out.notification;

import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.port.out.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação simulada de NotificationSender — apenas loga.
 * Substituta futura: email, push notification ou SMS sem tocar no domínio.
 */
@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(Notification notification) {
        log.info("[NOTIFICAÇÃO SIMULADA] Usuário: {} | Tipo: {} | Título: {}",
                notification.userId(), notification.type(), notification.title());
    }
}
