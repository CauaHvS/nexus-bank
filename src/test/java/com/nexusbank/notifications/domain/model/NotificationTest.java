package com.nexusbank.notifications.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final String USER_ID = "user-abc-123";

    @Test
    void create_deveInstanciarComReadFalseECreatedAtPreenchido() {
        Instant antes = Instant.now();

        Notification notification = Notification.create(USER_ID, NotificationType.ACCOUNT_OPENED,
                "Conta aberta", "Sua conta foi aberta com sucesso.");

        Instant depois = Instant.now();

        assertThat(notification.isRead()).isFalse();
        assertThat(notification.createdAt()).isNotNull();
        assertThat(notification.createdAt()).isAfterOrEqualTo(antes);
        assertThat(notification.createdAt()).isBeforeOrEqualTo(depois);
    }

    @Test
    void create_comUserId_deveSetarUserIdCorretamente() {
        Notification notification = Notification.create(USER_ID, NotificationType.TRANSFER_COMPLETED,
                "Transferência concluída", "Sua transferência foi concluída.");

        assertThat(notification.userId()).isEqualTo(USER_ID);
        assertThat(notification.type()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
        assertThat(notification.title()).isEqualTo("Transferência concluída");
        assertThat(notification.body()).isEqualTo("Sua transferência foi concluída.");
        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getId().value()).isNotNull();
    }

    @Test
    void markAsRead_deveSetarReadTrue() {
        Notification notification = Notification.create(USER_ID, NotificationType.ACCOUNT_OPENED,
                "Conta aberta", "Sua conta foi aberta.");

        assertThat(notification.isRead()).isFalse();
        notification.markAsRead();

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markAsRead_emNotificacaoJaLida_naoDeveAlterarEstado() {
        // Invariante declarada no Javadoc: markAsRead() é idempotente — não lança erro
        Notification notification = Notification.create(USER_ID, NotificationType.ACCOUNT_OPENED,
                "Conta aberta", "Sua conta foi aberta.");

        notification.markAsRead();
        // segunda chamada não deve lançar exceção (idempotente)
        notification.markAsRead();

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void reconstitute_deveRestaurarEstadoCorretamente() {
        NotificationId id = NotificationId.of(UUID.randomUUID());
        Instant createdAt = Instant.parse("2025-01-15T10:30:00Z");

        Notification notification = Notification.reconstitute(
                id, USER_ID, NotificationType.TRANSFER_FAILED,
                "Transferência falhou", "Sua transferência falhou.", true, createdAt);

        assertThat(notification.getId()).isEqualTo(id);
        assertThat(notification.userId()).isEqualTo(USER_ID);
        assertThat(notification.type()).isEqualTo(NotificationType.TRANSFER_FAILED);
        assertThat(notification.title()).isEqualTo("Transferência falhou");
        assertThat(notification.body()).isEqualTo("Sua transferência falhou.");
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void create_comUserIdNulo_deveLancarNullPointerException() {
        assertThatThrownBy(() -> Notification.create(null, NotificationType.ACCOUNT_OPENED,
                "Título", "Corpo"))
                .isInstanceOf(NullPointerException.class);
    }
}
