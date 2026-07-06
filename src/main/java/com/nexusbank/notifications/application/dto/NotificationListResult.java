package com.nexusbank.notifications.application.dto;

import java.util.List;

public record NotificationListResult(
        List<NotificationView> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long unreadCount
) {}
