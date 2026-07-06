package com.nexusbank.payments;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedRunnable;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários de resiliência usando a API programática do Resilience4j.
 * Sem Spring, sem banco, sem infraestrutura — apenas os decorators nativos.
 * Prova os comportamentos de Retry, CircuitBreaker, Bulkhead e RateLimiter
 * configurados para o módulo Payments (OutboxPoller + TransferController).
 */
@ExtendWith(MockitoExtension.class)
class ResilienceTest {

    // ---------------------------------------------------------------------------
    // Retry
    // ---------------------------------------------------------------------------

    @Test
    void retry_deveRetentarAte3VezesAntesDeFalhar() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build();
        RetryRegistry registry = RetryRegistry.of(config);
        Retry retry = registry.retry("outboxPublish");

        AtomicInteger tentativas = new AtomicInteger(0);
        Runnable operacao = Retry.decorateRunnable(retry, () -> {
            tentativas.incrementAndGet();
            throw new RuntimeException("Falha simulada");
        });

        assertThatThrownBy(operacao::run).isInstanceOf(Exception.class);
        assertThat(tentativas.get())
                .as("Deve ter tentado exatamente 3 vezes antes de desistir")
                .isEqualTo(3);
    }

    @Test
    void retry_devePassarNaSegundaTentativa() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build();
        RetryRegistry registry = RetryRegistry.of(config);
        Retry retry = registry.retry("outboxPublish");

        AtomicInteger tentativas = new AtomicInteger(0);
        Runnable operacao = Retry.decorateRunnable(retry, () -> {
            if (tentativas.incrementAndGet() < 2) {
                throw new RuntimeException("Falha transitória");
            }
            // segunda tentativa passa — sem exceção
        });

        operacao.run(); // não deve lançar exceção
        assertThat(tentativas.get())
                .as("Deve ter tentado exatamente 2 vezes: uma falha e uma com sucesso")
                .isEqualTo(2);
    }

    // ---------------------------------------------------------------------------
    // CircuitBreaker
    // ---------------------------------------------------------------------------

    @Test
    void circuitBreaker_deveAbrirApos50PorCentoDeFalhas() throws Throwable {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(4)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        CircuitBreaker cb = cbRegistry.circuitBreaker("kafkaPublish");

        // Alterna: falha, sucesso, falha, sucesso → 50% de falha
        AtomicInteger calls = new AtomicInteger(0);
        CheckedRunnable operacao = CircuitBreaker.decorateCheckedRunnable(cb, () -> {
            if (calls.getAndIncrement() % 2 == 0) {
                throw new RuntimeException("Falha Kafka");
            }
        });

        // 4 chamadas para preencher a janela deslizante
        for (int i = 0; i < 4; i++) {
            try {
                operacao.run();
            } catch (Throwable ignored) {
            }
        }

        assertThat(cb.getState())
                .as("Circuito deve abrir quando taxa de falha atingir 50%")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void circuitBreaker_devePermanecerFechadoComTaxaAbaixoDaThreshold() throws Throwable {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(4)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        CircuitBreaker cb = cbRegistry.circuitBreaker("kafkaPublish");

        AtomicInteger calls = new AtomicInteger(0);
        // 1 falha em 4 chamadas = 25% — abaixo do threshold de 50%
        CheckedRunnable operacao = CircuitBreaker.decorateCheckedRunnable(cb, () -> {
            if (calls.getAndIncrement() == 0) {
                throw new RuntimeException("Única falha");
            }
        });

        for (int i = 0; i < 4; i++) {
            try {
                operacao.run();
            } catch (Throwable ignored) {
            }
        }

        assertThat(cb.getState())
                .as("Circuito deve permanecer fechado com 25% de falhas (abaixo de 50%)")
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ---------------------------------------------------------------------------
    // Bulkhead
    // ---------------------------------------------------------------------------

    @Test
    void bulkhead_deveRejeitarQuandoLimiteAtingido() {
        BulkheadConfig bkConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(2)
                .maxWaitDuration(Duration.ofMillis(0))
                .build();
        BulkheadRegistry bkRegistry = BulkheadRegistry.of(bkConfig);
        Bulkhead bulkhead = bkRegistry.bulkhead("transferInitiate");

        // Simula 2 chamadas já em andamento — semáforo adquirido
        bulkhead.acquirePermission();
        bulkhead.acquirePermission();

        // Terceira tentativa deve ser rejeitada imediatamente
        assertThatThrownBy(bulkhead::acquirePermission)
                .as("Deve lançar BulkheadFullException quando o limite de chamadas simultâneas é atingido")
                .isInstanceOf(BulkheadFullException.class);

        // Limpar para não vazar estado
        bulkhead.releasePermission();
        bulkhead.releasePermission();
    }

    @Test
    void bulkhead_devePermitirAposLiberacaoDePermissao() {
        BulkheadConfig bkConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ofMillis(0))
                .build();
        BulkheadRegistry bkRegistry = BulkheadRegistry.of(bkConfig);
        Bulkhead bulkhead = bkRegistry.bulkhead("transferInitiate-release");

        bulkhead.acquirePermission();

        // Com o limite atingido, a segunda falha
        assertThatThrownBy(bulkhead::acquirePermission)
                .isInstanceOf(BulkheadFullException.class);

        // Após liberar, deve aceitar novamente
        bulkhead.releasePermission();
        boolean adquirido = bulkhead.tryAcquirePermission();
        assertThat(adquirido)
                .as("Deve aceitar nova permissão após liberar a anterior")
                .isTrue();
        bulkhead.releasePermission();
    }

    // ---------------------------------------------------------------------------
    // RateLimiter
    // ---------------------------------------------------------------------------

    @Test
    void rateLimiter_deveBloquearAposLimiteDeRequisicoes() {
        RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                .limitForPeriod(3)
                .limitRefreshPeriod(Duration.ofSeconds(60)) // janela longa: não renova durante o teste
                .timeoutDuration(Duration.ofMillis(0))
                .build();
        RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(rlConfig);
        RateLimiter rateLimiter = rlRegistry.rateLimiter("transferEndpoint");

        // 3 primeiras chamadas dentro do limite
        for (int i = 0; i < 3; i++) {
            boolean permitido = rateLimiter.acquirePermission();
            assertThat(permitido)
                    .as("Chamada %d deve ser permitida (dentro do limite de 3)", i + 1)
                    .isTrue();
        }

        // 4ª chamada deve ser bloqueada
        boolean bloqueado = rateLimiter.acquirePermission();
        assertThat(bloqueado)
                .as("4ª chamada deve ser rejeitada pois o limite de 3 requisições foi atingido")
                .isFalse();
    }

    @Test
    void rateLimiter_devePermitirExatamenteOLimitePorPeriodo() {
        final int LIMITE = 5;
        RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                .limitForPeriod(LIMITE)
                .limitRefreshPeriod(Duration.ofSeconds(60))
                .timeoutDuration(Duration.ofMillis(0))
                .build();
        RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(rlConfig);
        RateLimiter rateLimiter = rlRegistry.rateLimiter("transferEndpoint-contagem");

        int permitidas = 0;
        int bloqueadas = 0;

        for (int i = 0; i < LIMITE + 3; i++) {
            if (rateLimiter.acquirePermission()) {
                permitidas++;
            } else {
                bloqueadas++;
            }
        }

        assertThat(permitidas)
                .as("Exatamente %d requisições devem ser permitidas no período", LIMITE)
                .isEqualTo(LIMITE);
        assertThat(bloqueadas)
                .as("As 3 requisições excedentes devem ser bloqueadas")
                .isEqualTo(3);
    }
}
