package com.nexusbank.identity.application.usecase;

import com.nexusbank.identity.application.dto.AuthResult;
import com.nexusbank.identity.domain.exception.InvalidCredentialsException;
import com.nexusbank.identity.domain.model.UserId;
import com.nexusbank.identity.domain.port.out.TokenStore;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    private static final String TEST_SECRET = "nexus-bank-test-secret-256-bits-long-xyz";
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String ROLE = "CUSTOMER";

    @Mock
    TokenStore tokenStore;

    JwtService jwtService;
    RefreshTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 900L, 604800L);
        useCase = new RefreshTokenUseCase(tokenStore, jwtService, 604800L);
    }

    @Test
    @DisplayName("token valido retorna novo par access+refresh")
    void execute_withValidToken_returnsNewTokenPair() {
        AuthResult initial = jwtService.generateTokenPair(USER_ID, ROLE);
        String oldHash = TokenHasher.hash(initial.refreshToken());
        UserId userId = UserId.of(USER_ID);

        when(tokenStore.validateAndRotate(eq(oldHash), any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(userId));

        AuthResult result = useCase.execute(initial.refreshToken());

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.refreshToken()).isNotEqualTo(initial.refreshToken());
        assertThat(result.accessToken()).isNotEqualTo(initial.accessToken());
    }

    @Test
    @DisplayName("token revogado (Redis retorna empty) lanca InvalidCredentialsException")
    void execute_withRevokedToken_throwsInvalidCredentialsException() {
        AuthResult initial = jwtService.generateTokenPair(USER_ID, ROLE);

        when(tokenStore.validateAndRotate(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(initial.refreshToken()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("JWT com expiracao no passado lanca InvalidCredentialsException")
    void execute_withExpiredJwt_throwsInvalidCredentialsException() {
        String expiredToken = buildExpiredRefreshToken();

        // tokenStore nao deve ser chamado: falha antes na validacao JWT
        assertThatThrownBy(() -> useCase.execute(expiredToken))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("token com type=access (nao refresh) lanca InvalidCredentialsException")
    void execute_withWrongTokenType_throwsInvalidCredentialsException() {
        // generateTokenPair gera access + refresh; usamos o accessToken aqui
        AuthResult pair = jwtService.generateTokenPair(USER_ID, ROLE);

        assertThatThrownBy(() -> useCase.execute(pair.accessToken()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("replay attack: segunda chamada com mesmo token deve falhar")
    void execute_calledTwice_secondCallFails() {
        AuthResult initial = jwtService.generateTokenPair(USER_ID, ROLE);
        String oldHash = TokenHasher.hash(initial.refreshToken());
        UserId userId = UserId.of(USER_ID);

        // Primeira chamada: Redis aceita e rotaciona
        when(tokenStore.validateAndRotate(eq(oldHash), any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(userId));

        AuthResult first = useCase.execute(initial.refreshToken());
        assertThat(first).isNotNull();

        // Segunda chamada com o mesmo token original: Redis ja nao o conhece
        when(tokenStore.validateAndRotate(eq(oldHash), any(String.class), any(Duration.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(initial.refreshToken()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // Constroi um JWT refresh com expiracao 1 segundo no passado usando a mesma chave de teste
    private String buildExpiredRefreshToken() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(10);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(USER_ID)
                .claim("role", ROLE)
                .claim("type", "refresh")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();
    }
}
