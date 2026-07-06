package com.nexusbank.notifications.domain.event;

import com.nexusbank.notifications.domain.model.NotificationId;
import com.nexusbank.notifications.domain.model.NotificationType;

public record NotificationCreated(
        NotificationId notificationId,
        String userId,
        NotificationType type
) {}
