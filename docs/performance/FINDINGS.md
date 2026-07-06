# Relatório de Performance — Fatia 7.2

## Contexto

Testes executados com k6 contra a stack completa via docker compose.
Ambiente: máquina de desenvolvimento local (não produção).

## Configurações de resiliência ativas

| Padrão       | Parâmetro                        | Valor configurado |
|--------------|----------------------------------|-------------------|
| Rate Limiter | `transferEndpoint` limite/s      | 10 req/s          |
| Bulkhead     | `transferInitiate` max concurrent| 20                |
| Retry        | `outboxPublish` tentativas       | 3 (backoff exp.)  |
| Circuit Breaker | `kafkaPublish` threshold      | 50%, janela 10    |

## Resultados — Transferências (POST /transfers)

### Cenário: `load` (20 VUs, ~2 min)

| Métrica                    | Valor medido | Threshold |
|----------------------------|-------------|-----------|
| Total de requisições       | —           | —         |
| 201 Criadas                | —           | —         |
| 429 Rate Limited           | —           | —         |
| 409 Conflito (OCC)         | —           | —         |
| 503 Bulkhead Full          | —           | —         |
| p95 latência (201)         | — ms        | < 600ms   |
| p99 latência (201)         | — ms        | < 1200ms  |

### Cenário: `stress` (60 VUs)

| Métrica                    | Valor medido | Observação                         |
|----------------------------|--------------|------------------------------------|
| Ponto de saturação (VUs)   | —            | Quando 429 > 50% das requisições   |
| p95 ao atingir saturação   | — ms         |                                    |
| Comportamento pós-ruptura  | —            | Sistema se recuperou? Em quanto tempo? |

## Resultados — Leitura (GET balance + statement)

### Cenário: `cache-warm` (50 VUs, 2 min)

| Métrica              | Valor medido | Threshold |
|----------------------|-------------|-----------|
| Balance p95          | — ms        | < 150ms   |
| Balance p99          | — ms        | < 300ms   |
| Statement p95        | — ms        | < 300ms   |
| Statement p99        | — ms        | < 600ms   |

### Cenário: `cache-miss` (30 VUs rotacionando)

| Métrica                    | Valor medido | Delta vs cache-warm |
|----------------------------|-------------|---------------------|
| Balance p95                | — ms        | —                   |
| Statement p95              | — ms        | —                   |

## Gargalos identificados

> Preencher após executar os testes.

### Gargalos esperados (pré-teste)

1. **Rate Limiter (10 req/s)** — Teto hard de throughput no endpoint de transferências.
   Com 20+ VUs cada um fazendo ~0.7 req/s, a taxa agregada ultrapassa o limite e
   aproximadamente `(taxa_real - 10) / taxa_real` das requisições retornam 429.

2. **Outbox Poller (2s delay)** — O POST /transfers retorna PENDING imediatamente,
   mas o saga listener demora até 2 segundos para processar via Kafka. Não impacta
   a latência da resposta do endpoint, mas aumenta o tempo percebido pelo usuário
   até o status COMPLETED.

3. **Leitura de extrato sem cache** — Primeira leitura de `statement` por conta busca
   no banco. Se o índice `(account_id, created_at)` não existir, degrada com o volume
   de movimentações.

## Otimizações aplicadas / recomendadas

> Preencher após análise.

| Otimização                          | Impacto esperado         | Status    |
|-------------------------------------|--------------------------|-----------|
| Ajustar rate limiter para 50 req/s  | Aumenta throughput       | Pendente  |
| Ajustar bulkhead para 50 concurrent | Reduz 503 sob stress     | Pendente  |
| Índice composto `(account_id, occurred_at DESC)` em account_entries | Reduz latência de leitura do extrato | Aplicado (V5) |

## Como reproduzir

```bash
# Subir a stack
docker compose up -d

# Teste de transferências (~7 min)
k6 run k6/transfer-load.js -e NUM_USERS=20

# Teste de leitura (~5 min)
k6 run k6/read-load.js -e NUM_USERS=10

# Resultados em JSON
# k6/results/transfer-load-summary.json
# k6/results/read-load-summary.json
```
