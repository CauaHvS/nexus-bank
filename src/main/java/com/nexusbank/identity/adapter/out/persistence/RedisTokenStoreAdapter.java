package com.nexusbank.identity.adapter.out.persistence;

import com.nexusbank.identity.domain.model.UserId;
import com.nexusbank.identity.domain.port.out.TokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Adaptador Redis para refresh tokens rotativos.
 * Chave de token:      "rt:{tokenHash}" -> userId
 * Chave de user index: "rt:user:{userId}" -> tokenHash atual (para revogar todos)
 *
 * Tokens nunca são armazenados em texto plano; apenas o hash SHA-256 é persistido.
 * A rotação invalida o token antigo e persiste o novo em dois comandos SET sequenciais.
 * Para atomicidade real num cluster Redis, um Lua script seria necessário — esta
 * implementação é adequada para sessão única por usuário (índice user -> hash).
 */
@Component
class RedisTokenStoreAdapter implements TokenStore {

    private static final String RT_PREFIX = "rt:";
    private static final String USER_PREFIX = "rt:user:";

    private final StringRedisTemplate redis;

    RedisTokenStoreAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void storeRefreshToken(UserId userId, String tokenHash, Duration ttl) {
        String userIdStr = userId.value().toString();
        redis.opsForValue().set(RT_PREFIX + tokenHash, userIdStr, ttl);
        redis.opsForValue().set(USER_PREFIX + userIdStr, tokenHash, ttl);
    }

    @Override
    public Optional<UserId> validateAndRotate(String oldTokenHash, String newTokenHash, Duration newTtl) {
        String userIdStr = redis.opsForValue().get(RT_PREFIX + oldTokenHash);
        if (userIdStr == null) {
            return Optional.empty();
        }

        redis.delete(RT_PREFIX + oldTokenHash);

        redis.opsForValue().set(RT_PREFIX + newTokenHash, userIdStr, newTtl);
        redis.opsForValue().set(USER_PREFIX + userIdStr, newTokenHash, newTtl);

        return Optional.of(UserId.of(userIdStr));
    }

    @Override
    public void revoke(String tokenHash) {
        String userIdStr = redis.opsForValue().get(RT_PREFIX + tokenHash);
        if (userIdStr != null) {
            redis.delete(USER_PREFIX + userIdStr);
        }
        redis.delete(RT_PREFIX + tokenHash);
    }

    @Override
    public void revokeAllForUser(UserId userId) {
        String userIdStr = userId.value().toString();
        String currentHash = redis.opsForValue().get(USER_PREFIX + userIdStr);
        if (currentHash != null) {
            redis.delete(RT_PREFIX + currentHash);
        }
        redis.delete(USER_PREFIX + userIdStr);
    }
}
