package com.nexusbank.notifications.adapter.in.web;

import com.nexusbank.notifications.application.dto.NotificationListResult;
import com.nexusbank.notifications.application.dto.NotificationView;
import com.nexusbank.notifications.application.usecase.GetNotificationsUseCase;
import com.nexusbank.notifications.application.usecase.MarkAllAsReadUseCase;
import com.nexusbank.notifications.application.usecase.MarkAsReadUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final MarkAsReadUseCase markAsReadUseCase;
    private final MarkAllAsReadUseCase markAllAsReadUseCase;

    public NotificationController(GetNotificationsUseCase getNotificationsUseCase,
                                   MarkAsReadUseCase markAsReadUseCase,
                                   MarkAllAsReadUseCase markAllAsReadUseCase) {
        this.getNotificationsUseCase = getNotificationsUseCase;
        this.markAsReadUseCase = markAsReadUseCase;
        this.markAllAsReadUseCase = markAllAsReadUseCase;
    }

    @GetMapping
    public NotificationListResult getNotifications(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getNotificationsUseCase.execute(userId, page, Math.min(size, 50));
    }

    @PatchMapping("/{id}/read")
    public NotificationView markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal String userId) {
        return markAsReadUseCase.execute(id, userId);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(@AuthenticationPrincipal String userId) {
        markAllAsReadUseCase.execute(userId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal String userId) {
        return Map.of("unreadCount", getNotificationsUseCase.countUnread(userId));
    }
}
