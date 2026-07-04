package com.nexusbank.corebanking.adapter.out.persistence;

import com.nexusbank.corebanking.domain.model.AccountId;
import com.nexusbank.corebanking.domain.model.Currency;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.corebanking.domain.port.out.BalanceCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Cache de saldo no Redis.
 * Chave: "balance:{accountId}" -> "{amount}:{currency}"
 * TTL: 5 minutos. Invalidado a cada BalanceUpdated.
 */
@Component
class RedisBalanceCacheAdapter implements BalanceCache {

    private static final String PREFIX = "balance:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    RedisBalanceCacheAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(AccountId accountId, Money balance) {
        String value = balance.amount().toPlainString() + ":" + balance.currency().name();
        redis.opsForValue().set(PREFIX + accountId.value(), value, TTL);
    }

    @Override
    public Optional<Money> get(AccountId accountId) {
        String raw = redis.opsForValue().get(PREFIX + accountId.value());
        if (raw == null) return Optional.empty();
        String[] parts = raw.split(":");
        return Optional.of(Money.of(new BigDecimal(parts[0]), Currency.valueOf(parts[1])));
    }

    @Override
    public void evict(AccountId accountId) {
        redis.delete(PREFIX + accountId.value());
    }
}
