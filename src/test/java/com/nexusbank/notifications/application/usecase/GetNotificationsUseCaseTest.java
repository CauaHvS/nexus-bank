package com.nexusbank.notifications.application.usecase;

import com.nexusbank.notifications.application.dto.NotificationListResult;
import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.model.NotificationType;
import com.nexusbank.notifications.domain.port.out.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetNotificationsUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;

    private GetNotificationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetNotificationsUseCase(notificationRepository);
    }

    @Test
    void execute_deveRetornarNotificacoesComUnreadCount() {
        // Arrange
        String userId = "user-abc";
        Notification n1 = Notification.create(userId, NotificationType.ACCOUNT_OPENED,
                "Conta aberta", "Sua conta foi aberta.");
        Notification n2 = Notification.create(userId, NotificationType.TRANSFER_COMPLETED,
                "Transferência concluída", "Transferência de BRL 100,00 concluída.");

        List<Notification> lista = List.of(n1, n2);
        PageImpl<Notification> page = new PageImpl<>(lista, PageRequest.of(0, 10), 2);

        when(notificationRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(page);
        when(notificationRepository.countUnread(userId)).thenReturn(1L);

        // Act
        NotificationListResult result = useCase.execute(userId, 0, 10);

        // Assert
        assertThat(result.content()).hasSize(2);
        assertThat(result.unreadCount()).isEqualTo(1L);
        assertThat(result.page()).isZero();
        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void execute_listaVazia_deveRetornarResultadoVazio() {
        // Arrange
        String userId = "user-sem-notificacoes";
        PageImpl<Notification> pageVazia = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        when(notificationRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(pageVazia);
        when(notificationRepository.countUnread(userId)).thenReturn(0L);

        // Act
        NotificationListResult result = useCase.execute(userId, 0, 10);

        // Assert
        assertThat(result.content()).isEmpty();
        assertThat(result.unreadCount()).isZero();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void execute_deveMapearCamposDeNotificacaoCorretamente() {
        // Arrange
        String userId = "user-map";
        Notification notif = Notification.create(userId, NotificationType.TRANSFER_COMPLETED,
                "Título da notificação", "Corpo da notificação.");
        PageImpl<Notification> page = new PageImpl<>(List.of(notif), PageRequest.of(0, 5), 1);

        when(notificationRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(page);
        when(notificationRepository.countUnread(userId)).thenReturn(1L);

        // Act
        NotificationListResult result = useCase.execute(userId, 0, 5);

        // Assert — campos do NotificationView mapeados corretamente
        var view = result.content().getFirst();
        assertThat(view.userId()).isEqualTo(userId);
        assertThat(view.type()).isEqualTo(NotificationType.TRANSFER_COMPLETED.name());
        assertThat(view.title()).isEqualTo("Título da notificação");
        assertThat(view.body()).isEqualTo("Corpo da notificação.");
        assertThat(view.read()).isFalse();
        assertThat(view.id()).isNotBlank();
    }

    @Test
    void countUnread_deveDelegarAoRepository() {
        // Arrange
        String userId = "user-count";
        when(notificationRepository.countUnread(userId)).thenReturn(5L);

        // Act
        long count = useCase.countUnread(userId);

        // Assert
        assertThat(count).isEqualTo(5L);
    }
}
