package com.nexusbank.payments.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusbank.corebanking.CoreBankingApi;
import com.nexusbank.corebanking.domain.exception.AccountConcurrentModificationException;
import com.nexusbank.corebanking.domain.model.Money;
import com.nexusbank.fraud.FraudApi;
import com.nexusbank.fraud.FraudDecision;
import com.nexusbank.payments.domain.port.out.TransferMetricsPort;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import com.nexusbank.payments.application.dto.InitiateTransferCommand;
import com.nexusbank.payments.application.dto.TransferResult;
import com.nexusbank.payments.domain.model.IdempotencyKey;
import com.nexusbank.payments.domain.model.Transfer;
import com.nexusbank.payments.domain.port.out.OutboxRepository;
import com.nexusbank.payments.domain.port.out.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcurrencyTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private CoreBankingApi coreBankingApi;

    @Mock
    private FraudApi fraudApi;

    @Mock
    private TransferMetricsPort metrics;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    private InitiateTransferUseCase useCase;

    private static final String SOURCE = "account-1";
    private static final String TARGET = "account-2";
    private static final String USER_ID = "user-dono-001";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        when(fraudApi.evaluate(any())).thenReturn(FraudDecision.APPROVED);
        org.mockito.Mockito.lenient().when(tracer.nextSpan()).thenReturn(span);
        org.mockito.Mockito.lenient().when(span.name(any())).thenReturn(span);
        org.mockito.Mockito.lenient().when(span.start()).thenReturn(span);
        org.mockito.Mockito.lenient().when(span.tag(anyString(), anyString())).thenReturn(span);
        org.mockito.Mockito.lenient().when(tracer.withSpan(any())).thenReturn(() -> {});
        useCase = new InitiateTransferUseCase(transferRepository, outboxRepository, coreBankingApi, fraudApi, objectMapper, metrics, tracer);
    }

    private InitiateTransferCommand buildCommand(String idempotencyKey) {
        return new InitiateTransferCommand(
                SOURCE, TARGET,
                new BigDecimal("1.00"), "BRL",
                "INTERNAL", idempotencyKey,
                "Débito concorrente",
                USER_ID
        );
    }

    /**
     * Prova que quando N threads tentam debitar simultaneamente uma conta com saldo
     * suficiente apenas para um débito, exatamente uma thread sucede e as demais
     * recebem AccountConcurrentModificationException.
     *
     * O mock simula o lock otimista: apenas a primeira thread que chama debit() passa;
     * as demais lançam AccountConcurrentModificationException.
     */
    @Test
    void debitos_simultaneos_apenas_um_completa_sem_saldo_negativo() throws InterruptedException {
        final int N = 5;
        final AtomicBoolean debitado = new AtomicBoolean(false);
        final AtomicInteger saldo = new AtomicInteger(100);

        // Cada thread usa chave de idempotência única — não há duplicatas intencionais
        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());

        when(coreBankingApi.isOwner(SOURCE, USER_ID)).thenReturn(true);

        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Simula lock otimista: primeira thread subtrai saldo; demais lançam conflito
        doAnswer(inv -> {
            if (debitado.compareAndSet(false, true)) {
                saldo.addAndGet(-100);
                return null;
            }
            throw new AccountConcurrentModificationException(SOURCE);
        }).when(coreBankingApi).debit(anyString(), any(Money.class), anyString());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(N);
        ExecutorService executor = Executors.newFixedThreadPool(N);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            final String idemKey = "chave-concorrencia-teste1-" + i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await(); // todas aguardam para partir juntas
                    return useCase.execute(buildCommand(idemKey));
                } catch (AccountConcurrentModificationException | InterruptedException e) {
                    return e;
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown(); // dispara todas as threads simultaneamente
        boolean concluido = doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(concluido).as("Todas as threads devem terminar em até 5 segundos").isTrue();

        long successCount = futures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { return e; }
                })
                .filter(r -> r instanceof TransferResult)
                .count();

        long conflictCount = futures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { return e; }
                })
                .filter(r -> r instanceof AccountConcurrentModificationException)
                .count();

        assertThat(successCount)
                .as("Exatamente um débito deve ser realizado com sucesso")
                .isEqualTo(1);

        assertThat(conflictCount)
                .as("As demais threads devem receber AccountConcurrentModificationException")
                .isEqualTo(N - 1);

        assertThat(saldo.get())
                .as("O saldo deve ser decrementado exatamente uma vez: 100 - 100 = 0")
                .isEqualTo(0);
    }

    /**
     * Prova que quando N threads tentam debitar uma conta com saldo para exatamente
     * metade dos débitos, o saldo nunca fica negativo.
     *
     * O mock usa AtomicInteger como contador: as primeiras 5 chamadas passam,
     * as demais lançam AccountConcurrentModificationException.
     */
    @Test
    void debitos_simultaneos_N_threads_sem_saldo_negativo() throws InterruptedException {
        final int N = 10;
        final int DEBITOS_PERMITIDOS = 5;
        final AtomicInteger contador = new AtomicInteger(0);
        final AtomicInteger saldo = new AtomicInteger(500);

        when(transferRepository.findByIdempotencyKey(any(IdempotencyKey.class)))
                .thenReturn(Optional.empty());

        when(coreBankingApi.isOwner(SOURCE, USER_ID)).thenReturn(true);

        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Simula lock otimista: primeiras DEBITOS_PERMITIDOS chamadas passam; demais falham
        doAnswer(inv -> {
            int posicao = contador.getAndIncrement();
            if (posicao < DEBITOS_PERMITIDOS) {
                saldo.addAndGet(-100);
                return null;
            }
            throw new AccountConcurrentModificationException(SOURCE);
        }).when(coreBankingApi).debit(anyString(), any(Money.class), anyString());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(N);
        ExecutorService executor = Executors.newFixedThreadPool(N);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            final String idemKey = "chave-concorrencia-teste2-" + i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    return useCase.execute(buildCommand(idemKey));
                } catch (AccountConcurrentModificationException | InterruptedException e) {
                    return e;
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        boolean concluido = doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(concluido).as("Todas as threads devem terminar em até 5 segundos").isTrue();

        List<Object> resultados = futures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { return e; }
                })
                .toList();

        long successCount = resultados.stream()
                .filter(r -> r instanceof TransferResult)
                .count();

        long conflictCount = resultados.stream()
                .filter(r -> r instanceof AccountConcurrentModificationException)
                .count();

        assertThat(saldo.get())
                .as("O saldo nunca deve ficar negativo")
                .isGreaterThanOrEqualTo(0);

        assertThat(successCount)
                .as("Exatamente 5 débitos devem ser realizados com sucesso")
                .isEqualTo(5);

        assertThat(conflictCount)
                .as("Exatamente 5 threads devem receber AccountConcurrentModificationException")
                .isEqualTo(5);
    }
}
