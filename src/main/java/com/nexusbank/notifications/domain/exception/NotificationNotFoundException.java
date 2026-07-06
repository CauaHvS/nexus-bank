package com.nexusbank.notifications.domain.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String id) {
        super("Notificação não encontrada: " + id);
    }
}
