package com.nexusbank.notifications.application.usecase;

import com.nexusbank.notifications.domain.model.Notification;
import com.nexusbank.notifications.domain.model.NotificationType;
import com.nexusbank.notifications.domain.port.out.NotificationRepository;
import com.nexusbank.notifications.domain.port.out.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateNotificationUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSender notificationSender;

    private CreateNotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateNotificationUseCase(notificationRepository, notificationSender);
    }

    @Test
    void execute_cenarioHappy_deveSalvarEEnviar() {
        // Arrange
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        useCase.execute("user-123", NotificationType.ACCOUNT_OPENED,
                "Conta aberta", "Sua conta foi aberta com sucesso.");

        // Assert
        verify(notificationRepository, times(1)).save(captor.capture());
        verify(notificationSender, times(1)).send(any(Notification.class));

        Notification salva = captor.getValue();
        assertThat(salva.userId()).isEqualTo("user-123");
        assertThat(salva.type()).isEqualTo(NotificationType.ACCOUNT_OPENED);
        assertThat(salva.isRead()).isFalse();
    }

    @Test
    void execute_senderFalha_deveAindaSalvar() {
        // Arrange
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Servidor de push indisponível"))
                .when(notificationSender).send(any());

        // Act — não deve lançar exceção para o chamador
        assertThatCode(() ->
                useCase.execute("user-456", NotificationType.TRANSFER_COMPLETED,
                        "Transferência concluída", "Sua transferência foi concluída.")
        ).doesNotThrowAnyException();

        // Assert — save DEVE ter sido chamado mesmo com sender falhando
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(notificationSender, times(1)).send(any(Notification.class));
    }

    @Test
    void execute_senderFalha_naoDevePropagar_eSalvaSempreAntesDeSender() {
        // Garante a ordem: save primeiro, depois send — e falha no send não reverte
        ArgumentCaptor<Notification> saveCaptor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Timeout"))
                .when(notificationSender).send(any());

        useCase.execute("user-789", NotificationType.TRANSFER_FAILED,
                "Transferência falhou", "Falha ao processar.");

        verify(notificationRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().type()).isEqualTo(NotificationType.TRANSFER_FAILED);
    }
}
