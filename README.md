# Nexus Bank

Banco digital distribuído implementado como **monólito modular** (Spring Modulith). Cada bounded context é um módulo com arquitetura hexagonal interna; a comunicação entre módulos ocorre por eventos de domínio e Kafka. O módulo de Notifications foi extraído para um microsserviço físico independente ao final do projeto, demonstrando decomposição controlada.

> Projeto de portfólio sênior que demonstra padrões de sistemas distribuídos (Saga, Outbox, CQRS, consistência eventual, idempotência) sem os custos operacionais de microsserviços prematuros.

---

## Por que monólito modular?

Microsserviços físicos desde o início, solo, produzem um *distributed monolith*: todo o custo operacional, nenhum benefício real. O monólito modular:

- Aplica os **mesmos padrões** (Saga, Outbox, CQRS, idempotência, consistência eventual) dentro de um único deployable
- Tem **fronteiras fortes** entre módulos verificadas automaticamente pelo Spring Modulith (`ApplicationModules.verify()`)
- Permite **extração seletiva** quando há razão real (o módulo Notifications demonstra isso)

Ver [ADR-002](docs/adr/ADR-002-monolito-modular-vs-microservicos.md).

---

## Bounded Contexts e Módulos

| Módulo         | Responsabilidade                                                                                      |
|----------------|-------------------------------------------------------------------------------------------------------|
| **Identity**   | Registro, login, JWT stateless, refresh rotativo, MFA TOTP                                            |
| **Core Banking** | Abertura de conta, saldo, extrato (CQRS read model), cache Redis                                    |
| **Payments**   | Saga de transferência + Transactional Outbox, idempotência, concorrência (locking otimista), PIX/TED/agendamento |
| **Fraud**      | Score de risco por regras (valor, frequência, destino novo), UNDER\_REVIEW, auditoria                 |
| **Notifications** | Microsserviço extraído; consome eventos Kafka e simula envio de e-mail/push                        |

---

## Padrões implementados

### Saga + Transactional Outbox
Transferência entre contas usa coreografia Saga: `InitiateTransferUseCase` debita a origem e grava `TransferInitiated` na tabela de outbox **na mesma transação**. O `OutboxPoller` publica ao Kafka a cada 2 segundos. O `TransferSagaListener` consome e chama `CompleteTransferUseCase` para creditar o destino. Falha no crédito dispara `CompensateTransferUseCase` (estorno).

### Idempotência
`POST /transfers` exige header `Idempotency-Key`. Mesma chave na mesma conta retorna o resultado original sem duplo débito.

### CQRS (leitura separada)
Extrato e saldo usam read model projetado a partir de `AccountEntry`s, separado do agregado de escrita. Saldo cacheado no Redis com invalidação por evento.

### Controle de concorrência
`Account` usa `@Version` (locking otimista). Dois débitos simultâneos na mesma conta resultam em `OptimisticLockException` → HTTP 409. Testado com `CountDownLatch` + `ExecutorService`.

### Resiliência (Resilience4j)
- `@Retry` na publicação do Outbox (3 tentativas, backoff exponencial)
- `@CircuitBreaker` no Kafka publish (50% threshold, janela 10)
- `@Bulkhead` no endpoint de transferências (20 chamadas simultâneas)
- `@RateLimiter` no endpoint de transferências (10 req/s)

---

## Stack

| Camada         | Tecnologia                                                                              |
|----------------|-----------------------------------------------------------------------------------------|
| Backend        | Java 21, Spring Boot 3.5, Spring Modulith 1.3, Maven                                   |
| Persistência   | PostgreSQL 16, Spring Data JPA, Flyway (migrations por módulo)                         |
| Mensageria     | Apache Kafka (Confluent 7.6), Transactional Outbox                                     |
| Cache / Auth   | Redis 7, JWT (jjwt 0.12), TOTP (dev.samstevens.totp)                                  |
| Resiliência    | Resilience4j 2.2 (circuit breaker, retry, bulkhead, rate limiter)                      |
| Frontend       | React 18, TypeScript, Vite 5, TailwindCSS 3, TanStack Query v5, React Router v6, Axios |
| Observabilidade| Micrometer, Prometheus, Grafana, Loki, Tempo, OpenTelemetry                             |
| Testes         | JUnit 5, Mockito, AssertJ, Testcontainers, Playwright (E2E), k6 (carga)                |
| Infra          | Docker, Docker Compose, Nginx, GitHub Actions                                           |

---

## Como executar

### Pré-requisitos
- Docker e Docker Compose v2
- Java 21 (para rodar localmente sem Docker)
- Node 20 (para o frontend em modo dev)

### Stack completa (recomendado)

```bash
# Sobe infra (Postgres, Redis, Kafka) + app (core, notifications, frontend/Nginx) + observabilidade
docker compose --profile infra --profile app --profile observability up -d --build

# Apenas infra + app (sem Grafana/Prometheus/Loki/Tempo)
docker compose --profile infra --profile app up -d --build
```

Depois de subir:
- Frontend: http://localhost:80
- Swagger UI (core): http://localhost:8080/swagger-ui.html
- Grafana: http://localhost:3000 (admin / admin)
- Prometheus: http://localhost:9090

### Desenvolvimento local (sem Docker para o app)

```bash
# 1. Sobe apenas a infra
docker compose --profile infra up -d

# 2. Backend
./mvnw spring-boot:run

# 3. Frontend
cd frontend && npm install && npm run dev
# → http://localhost:5173
```

---

## Testes

### Backend

```bash
# Unitários + integração + verify de módulos (requer Docker para Testcontainers)
./mvnw clean verify
```

A suíte inclui:
- **161 testes unitários**: domínio e casos de uso (sem framework, só Mockito)
- **Testes de integração**: Testcontainers (Postgres, Redis, Kafka reais)
- **Teste de concorrência**: CountDownLatch + 20 threads concorrentes em débito — prova que saldo nunca fica negativo
- **Verify de módulos**: `ApplicationModules.verify()` garante que nenhum módulo viola a fronteira de outro

### Frontend

```bash
cd frontend
npm run lint   # TypeScript sem erros
npm run test   # Vitest + Testing Library
npm run build  # Build de produção
```

### E2E (Playwright)

```bash
# Requer stack rodando (docker compose ou local)
cd e2e
npm install
npx playwright install --with-deps chromium
npx playwright test
```

9 testes cobrindo: cadastro, login, abertura de conta, transferência, extrato, notificações e conflito 409.

### Carga (k6)

```bash
# Requer k6 instalado e stack rodando
k6 run k6/transfer-load.js   # POST /transfers: load + stress (~7 min)
k6 run k6/read-load.js       # GET balance/statement: cache-warm + cache-miss (~5 min)
```

Thresholds: p95 de transferências aceitas < 600ms, p95 de leitura com cache < 150ms.

---

## Observabilidade

Com a stack de observabilidade rodando (`--profile observability`):

| Sinal   | Ferramenta     | Caminho                                    |
|---------|----------------|--------------------------------------------|
| Métricas| Prometheus + Grafana | http://localhost:3000 (dashboard pré-configurado) |
| Logs    | Loki + Grafana | Explore → Loki → `{job="nexus-bank-core"}` |
| Tracing | Tempo + Grafana| Explore → Tempo → TraceID                  |

Métricas de negócio customizadas: `nexusbank.transfers.total`, `nexusbank.transfers.completed`, `nexusbank.transfers.failed`, `nexusbank.fraud.reviews`.

Para rastrear uma transferência ponta a ponta: faça uma transferência, copie o `traceId` do log e cole no Explore do Tempo.

---

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`):

| Job       | Quando roda           | O que faz                                               |
|-----------|-----------------------|---------------------------------------------------------|
| `backend` | Todo push/PR          | `./mvnw clean verify` (unit + IT + verify de módulos)   |
| `frontend`| Todo push/PR          | lint (tsc) + vitest + build                             |
| `e2e`     | Push para master      | `docker compose up`, aguarda health, Playwright chromium |

---

## ADRs

| ADR   | Decisão                                                         |
|-------|-----------------------------------------------------------------|
| [001](docs/adr/ADR-001-arquitetura-hexagonal-ddd.md)         | Arquitetura hexagonal + DDD por módulo                          |
| [002](docs/adr/ADR-002-monolito-modular-vs-microservicos.md) | Monólito modular em vez de microsserviços (com extração seletiva) |
| [003](docs/adr/ADR-003-auth-security.md)                     | JWT stateless + refresh rotativo + MFA TOTP opcional            |
| [004](docs/adr/ADR-004-corebanking-cqrs-read-model.md)       | CQRS: read model separado para extrato e saldo                  |
| [005](docs/adr/ADR-005-payments-saga-outbox.md)              | Saga + Transactional Outbox para transferências                 |
| [006](docs/adr/ADR-006-payments-source-account-ownership.md) | Validação de ownership antes do débito (prevenção de IDOR)      |
| [007](docs/adr/ADR-007-notifications-dlq-strategy.md)        | DLQ via tabela PostgreSQL para notificações com falha           |
| [008](docs/adr/ADR-008-notifications-account-opened-event-delivery.md) | AccountOpened via Kafka desde o início              |
| [009](docs/adr/ADR-009-notifications-architecture.md)        | Arquitetura do módulo de notificações                           |
| [010](docs/adr/ADR-010-notifications-microservice-extraction.md) | Extração do Notifications para microsserviço físico         |
| [011](docs/adr/ADR-011-fraud-payments-integration.md)        | Integração Fraud-Payments síncrona in-process via API pública   |

---

## Diagramas C4

Diagramas em Mermaid renderizáveis no GitHub: [docs/c4/C4.md](docs/c4/C4.md)

- **C1 — Contexto**: atores e sistemas externos
- **C2 — Containers**: deployables, infra e observabilidade
- **C3 — Componentes**: módulos internos do nexus-bank-core
- **Fluxo Saga**: sequência de uma transferência ponta a ponta

---

## Estrutura do projeto

```
nexus-bank/
├── src/main/java/com/nexusbank/
│   ├── identity/           # Módulo: autenticação e identidade
│   ├── corebanking/        # Módulo: contas, saldo, extrato
│   ├── payments/           # Módulo: transferências, saga, fraud review
│   ├── fraud/              # Módulo: score de risco e auditoria
│   └── infrastructure/     # Beans transversais: Security, Flyway, Kafka, OpenAPI
├── notifications-service/  # Microsserviço extraído de notificações
├── frontend/               # React 18 + Vite SPA
├── e2e/                    # Testes E2E com Playwright
├── k6/                     # Scripts de carga (k6)
├── docs/
│   ├── adr/                # Architecture Decision Records
│   ├── c4/                 # Diagramas C4 em Mermaid
│   ├── openapi/            # Contratos OpenAPI (design)
│   └── performance/        # Relatório de carga
├── observability/          # Configurações de Prometheus, Grafana, Loki, Tempo
├── docker-compose.yml      # Profiles: infra, app, observability
└── .github/workflows/      # CI/CD GitHub Actions
```

---

## O que este projeto demonstra

- **Arquitetura**: por que monólito modular em vez de microsserviços prematuros, e como extrair depois de forma controlada
- **Padrões distribuídos**: Saga + Transactional Outbox + idempotência numa transferência financeira real
- **Concorrência**: como impedir débito duplo/saldo negativo sob disputa (locking otimista + teste multi-thread)
- **CQRS**: consistência eventual, eventos entre bounded contexts com Kafka
- **Resiliência**: circuit breaker, retry, bulkhead, rate limiter com Resilience4j
- **Observabilidade**: métricas de negócio, logs estruturados correlacionados, tracing distribuído ponta a ponta
- **Full stack**: React com estados de loading/erro/conflito reais, E2E verde
- **Engenharia**: Docker, CI/CD com GitHub Actions, C4, ADRs, testes de carga
