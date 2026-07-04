package com.nexusbank.identity.application.usecase;

import com.nexusbank.identity.domain.exception.InvalidCredentialsException;
import com.nexusbank.identity.domain.model.UserId;
import com.nexusbank.identity.domain.port.out.TokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    private static final String TEST_SECRET = "nexus-bank-test-secret-256-bits-long-xyz";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String ROLE = "CUSTOMER";

    @Mock
    TokenStore tokenStore;

    JwtService jwtService;
    LogoutUseCase logoutUseCase;
    RefreshTokenUseCase refreshUseCase;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 900L, 604800L);
        logoutUseCase = new LogoutUseCase(tokenStore);
        refreshUseCase = new RefreshTokenUseCase(tokenStore, jwtService, 604800L);
    }

    @Test
    @DisplayName("logout chama tokenStore.revoke com o hash SHA-256, nunca o token em claro")
    void execute_withValidToken_revokesHashedToken() {
        String refreshToken = jwtService.generateTokenPair(USER_ID, ROLE).refreshToken();
        String expectedHash = TokenHasher.hash(refreshToken);

        logoutUseCase.execute(refreshToken);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(tokenStore).revoke(hashCaptor.capture());

        String capturedHash = hashCaptor.getValue();
        assertThat(capturedHash)
                .as("deve revogar o hash SHA-256, nao o token em claro")
                .isEqualTo(expectedHash)
                .doesNotContain(refreshToken)
                .hasSize(64); // SHA-256 = 64 hex chars
    }

    @Test
    @DisplayName("apos logout o RefreshTokenUseCase deve rejeitar o mesmo token")
    void execute_afterLogout_refreshShouldFail() {
        String refreshToken = jwtService.generateTokenPair(USER_ID, ROLE).refreshToken();
        String tokenHash = TokenHasher.hash(refreshToken);

        // Simula o estado pos-logout: revoke foi chamado, Redis nao reconhece mais o hash
        logoutUseCase.execute(refreshToken);

        // Agora o refresh tenta validar o mesmo token — Redis retorna empty (revogado)
        when(tokenStore.validateAndRotate(any(), any(), any(Duration.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshUseCase.execute(refreshToken))
                .isInstanceOf(InvalidCredentialsException.class);

        // Confirma que revoke foi chamado com o hash correto durante o logout
        verify(tokenStore).revoke(tokenHash);
    }
}
