package com.nexusbank.identity.application.usecase;

import com.nexusbank.identity.application.dto.AuthResult;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-ttl-seconds:900}") long accessTtl,
            @Value("${security.jwt.refresh-token-ttl-seconds:604800}") long refreshTtl) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTtl;
        this.refreshTokenTtlSeconds = refreshTtl;
    }

    public AuthResult generateTokenPair(String userId, String role) {
        String accessToken = buildToken(userId, role, accessTokenTtlSeconds, "access");
        String refreshToken = buildToken(userId, role, refreshTokenTtlSeconds, "refresh");
        return AuthResult.bearer(accessToken, refreshToken, accessTokenTtlSeconds);
    }

    private String buildToken(String userId, String role, long ttlSeconds, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
