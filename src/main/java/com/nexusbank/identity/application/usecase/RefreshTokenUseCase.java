package com.nexusbank.identity.application.usecase;

import com.nexusbank.identity.application.dto.AuthResult;
import com.nexusbank.identity.domain.exception.InvalidCredentialsException;
import com.nexusbank.identity.domain.port.out.TokenStore;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RefreshTokenUseCase {

    private final TokenStore tokenStore;
    private final JwtService jwtService;
    private final long refreshTtlSeconds;

    public RefreshTokenUseCase(TokenStore tokenStore,
                               JwtService jwtService,
                               @Value("${security.jwt.refresh-token-ttl-seconds:604800}") long refreshTtl) {
        this.tokenStore = tokenStore;
        this.jwtService = jwtService;
        this.refreshTtlSeconds = refreshTtl;
    }

    /**
     * Valida o refresh token, gera novo par e rotaciona no Redis.
     * Ordem deliberada: validar assinatura JWT antes de consultar Redis,
     * evitando busca desnecessária para tokens com assinatura inválida.
     * Em seguida, a rotação no Redis confirma que o token ainda está ativo
     * (não foi revogado nem já foi usado anteriormente — prevenção de replay).
     */
    public AuthResult execute(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.validateAndParse(refreshToken);
        } catch (Exception e) {
            throw new InvalidCredentialsException();
        }

        if (!"refresh".equals(claims.get("type"))) {
            throw new InvalidCredentialsException();
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        String oldHash = TokenHasher.hash(refreshToken);

        AuthResult newTokens = jwtService.generateTokenPair(userId, role);
        String newHash = TokenHasher.hash(newTokens.refreshToken());

        tokenStore.validateAndRotate(oldHash, newHash, Duration.ofSeconds(refreshTtlSeconds))
                .orElseThrow(InvalidCredentialsException::new);

        return newTokens;
    }
}
