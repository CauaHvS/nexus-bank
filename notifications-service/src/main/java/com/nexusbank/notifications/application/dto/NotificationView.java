package com.nexusbank.notifications.application.dto;

import java.time.Instant;

public record NotificationView(
        String id,
        String userId,
        String type,
        String title,
        String body,
        boolean read,
        Instant createdAt
) {}
