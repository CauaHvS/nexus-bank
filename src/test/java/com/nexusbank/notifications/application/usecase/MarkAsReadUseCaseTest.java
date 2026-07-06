package com.nexusbank.notifications.application.usecase;

import com.nexusbank.notifications.application.dto.NotificationView;
import com.nexusbank.notifications.domain.exception.NotificationNotFoundException;
import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.model.NotificationId;
import com.nexusbank.notifications.domain.model.NotificationType;
import com.nexusbank.notifications.domain.port.out.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkAsReadUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;

    private MarkAsReadUseCase markAsReadUseCase;
    private MarkAllAsReadUseCase markAllAsReadUseCase;

    @BeforeEach
    void setUp() {
        markAsReadUseCase = new MarkAsReadUseCase(notificationRepository);
        markAllAsReadUseCase = new MarkAllAsReadUseCase(notificationRepository);
    }

    @Test
    void execute_notificacaoExistente_deveMarcarComoLida() {
        // Arrange
        String userId = "user-mark";
        UUID uuid = UUID.randomUUID();
        String notificationId = uuid.toString();

        Notification notificacaoNaoLida = Notification.create(userId, NotificationType.ACCOUNT_OPENED,
                "Conta aberta", "Sua conta foi aberta.");
        assertThat(notificacaoNaoLida.isRead()).isFalse();

        when(notificationRepository.findByIdAndUserId(any(NotificationId.class), any()))
                .thenReturn(Optional.of(notificacaoNaoLida));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        NotificationView view = markAsReadUseCase.execute(notificacaoNaoLida.getId().toString(), userId);

        // Assert
        verify(notificationRepository, times(1)).save(any(Notification.class));
        assertThat(captor.getValue().isRead()).isTrue();
        assertThat(view.read()).isTrue();
        assertThat(view.userId()).isEqualTo(userId);
    }

    @Test
    void execute_notificacaoInexistente_deveLancarNotFoundException() {
        // Arrange
        String userId = "user-x";
        String notificationId = UUID.randomUUID().toString();

        when(notificationRepository.findByIdAndUserId(any(NotificationId.class), any()))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> markAsReadUseCase.execute(notificationId, userId))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining(notificationId);
    }

    @Test
    void markAllAsRead_deveDelegar() {
        // Arrange
        String userId = "user-all";

        // Act
        markAllAsReadUseCase.execute(userId);

        // Assert
        verify(notificationRepository, times(1)).markAllAsRead(userId);
    }

    @Test
    void execute_notificacaoJaLida_devePermanecer_lida() {
        // Arrange — notificação já lida reconstituída do banco
        String userId = "user-already-read";
        NotificationId id = NotificationId.generate();
        Notification notificacaoJaLida = Notification.reconstitute(
                id, userId, NotificationType.TRANSFER_COMPLETED,
                "Transferência concluída", "Já lida.", true, java.time.Instant.now());

        when(notificationRepository.findByIdAndUserId(any(NotificationId.class), any()))
                .thenReturn(Optional.of(notificacaoJaLida));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act — chamar em notificação já lida é idempotente (não lança)
        NotificationView view = markAsReadUseCase.execute(id.toString(), userId);

        // Assert
        assertThat(view.read()).isTrue();
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }
}
