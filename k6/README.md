# k6 — Testes de Carga (Fatia 7.2)

Scripts de performance para os endpoints críticos do nexus-bank.

## Pré-requisitos

- [k6](https://k6.io/docs/get-started/installation/) instalado localmente
- Stack rodando: `docker compose up -d` (ou ao menos postgres, kafka, redis, nexus-bank-core)

## Executar

### Teste de transferências (POST /transfers)

```bash
# Carga nominal + stress (cenário completo ~7 min)
k6 run k6/transfer-load.js

# Somente carga nominal (sobrescreve NUM_USERS e BASE_URL se necessário)
k6 run k6/transfer-load.js -e BASE_URL=http://localhost:8080 -e NUM_USERS=20

# Saída JSON do resumo salva em k6/results/transfer-load-summary.json
```

### Teste de leitura (GET balance + statement)

```bash
# Cache quente + cache miss (~5 min)
k6 run k6/read-load.js -e NUM_USERS=10
```

## Variáveis de ambiente

| Variável    | Default                    | Descrição                            |
|-------------|----------------------------|--------------------------------------|
| `BASE_URL`  | `http://localhost:8080`    | URL base da API do nexus-bank-core   |
| `NUM_USERS` | `20` (transfer) / `10` (read) | Usuários criados no setup        |

## Estrutura dos scripts

```
k6/
  lib/
    api.js      # helpers HTTP (register, login, createAccount, deposit, transfer, balance, statement)
    setup.js    # prepareUsers(n) — cria usuários, contas e faz depósito inicial
  transfer-load.js   # POST /transfers: cenários load + stress
  read-load.js       # GET balance/statement: cenários cache-warm + cache-miss
  results/           # gerado automaticamente pelos scripts (gitignore)
  README.md
```

## O que cada cenário mede

### transfer-load — `load` (carga nominal)
- 20 VUs, 2 minutos
- ~14 req/s (acima do rate limiter de 10 req/s — espera-se alguns 429s)
- **Threshold:** p95 das transferências aceitas (201) < 600ms

### transfer-load — `stress`
- Ramp até 60 VUs para saturar rate limiter (10 req/s) e bulkhead (20 concurrent)
- Documenta como o sistema se comporta além da capacidade configurada

### read-load — `cache-warm`
- 50 VUs lendo os mesmos accountIds (Redis serve o dado após a primeira leitura)
- **Threshold:** balance p95 < 150ms, statement p95 < 300ms

### read-load — `cache-miss`
- 30 VUs rotacionando entre contas diferentes para forçar cache miss
- Compara latência com e sem cache

## Resultados esperados (baseados na configuração atual)

| Métrica                     | Esperado       | Limite (threshold) |
|-----------------------------|----------------|--------------------|
| Transfer p95 (201)          | 100–400ms      | < 600ms            |
| Transfer p99 (201)          | 200–800ms      | < 1200ms           |
| Balance p95 (cache quente)  | 10–80ms        | < 150ms            |
| Statement p95 (cache quente)| 30–200ms       | < 300ms            |
| 429 Rate Limited (load)     | ~30% das req   | informativo        |
| 409 Conflito (OCC)          | < 5% das req   | informativo        |

## Onde estão os gargalos conhecidos

1. **Rate limiter**: `10 req/s` em `transferEndpoint` (Resilience4j). Qualquer throughput acima disso
   retorna 429. Configurável em `application.properties` via `resilience4j.ratelimiter`.

2. **Bulkhead**: `20 simultâneas` em `transferInitiate`. Excesso retorna 503.

3. **Outbox Poller**: o saga listener demora até 2 segundos para processar `TransferInitiated`
   do Kafka. A latência do POST /transfers em si é baixa (só escreve no DB + outbox), mas o
   tempo até COMPLETED é assíncrono.

4. **Leitura de extrato**: sem paginação agressiva, busca toda a tabela de movimentações por conta.
   Índice em `account_id` + `created_at` mitiga.

Cole os resultados reais em `docs/performance/FINDINGS.md`.
