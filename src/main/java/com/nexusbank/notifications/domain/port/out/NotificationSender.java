package com.nexusbank.notifications.domain.port.out;

import com.nexusbank.notifications.domain.model.Notification;

public interface NotificationSender {
    void send(Notification notification);
}
