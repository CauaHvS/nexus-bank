# Roadmap de Fatias — Distributed Bank

Monólito modular (Spring Modulith / modular-monolith) com bounded contexts como
módulos, event-driven interno, hexagonal por módulo. Fatias verticais ponta a ponta.
Contract First: contrato da API antes de backend e frontend; mockup (ui-design) antes
do frontend. Cada fatia entrega uma responsabilidade, é validada e commitada antes da
próxima.

Ordem dos módulos: Identity > Core Banking > Payments (diferencial) > Notifications
(+ extração para microsserviço) > Fraud. Investments e Cards-faturas ficam para v2.

Skills por fase (carregam sob demanda): planejamento usa project-planning, adr-writer,
event-storming; arquitetura usa hexagonal-architecture, modular-monolith,
domain-driven-design; API usa api-design, input-validation, object-mapping; dados usa
database-migrations, concurrency-control, caching; integração usa messaging-patterns,
distributed-patterns, resilience, background-jobs, api-client-typegen; frontend usa
ui-design, react-frontend, frontend-accessibility, react-performance; testes usa
unit-testing, integration-testing, frontend-testing, e2e-testing, performance-testing;
infra usa containerization, cicd, observability, secrets-management.

---

# Fase 0 — Fundação e Design

## Fatia 0.1 — Scaffolding
**Prompt:**
```
Iniciar o distributed-bank.

Architect define a estrutura modular-monolith (Spring Modulith): um módulo por
bounded context (identity, corebanking, payments, notifications, fraud), hexagonal
por dentro. Cria o ADR 001 (arquitetura hexagonal + DDD) e o ADR 002 (monólito
modular em vez de microsserviços, com extração seletiva planejada).

Backend-engineer cria o esqueleto Spring Boot 3 (Java 21 + Maven) com os módulos
vazios e o teste ApplicationModules.verify() passando. Sem regra de negócio.

Valide que compila, sobe e o teste de módulos passa.
```
**Valida:** compila, Spring Boot sobe, verify de módulos verde. **Commit**

## Fatia 0.2 — Persistência e Infra
**Prompt:**
```
Configurar PostgreSQL, Spring Data JPA, Flyway, Redis e Kafka (via docker-compose só
para dev). Uma migration inicial vazia. Health checks. Segredos por env var
(secrets-management), nunca hardcode.
Valide conexão com Postgres, Redis e Kafka. Sem entidades.
```
**Valida:** app conecta em Postgres/Redis/Kafka, Flyway roda. **Commit**

## Fatia 0.3 — Scaffolding do Frontend
**Prompt:**
```
Frontend-engineer cria o app React + TS + Vite: TailwindCSS, TanStack Query, React
Router, Axios (interceptor), RHF + Zod, estrutura feature-based, layout base, dark
mode, ErrorBoundary global e skeleton reutilizável.
Uma tela placeholder consumindo /health do backend.
Valide npm run dev e a chamada ao backend.
```
**Valida:** frontend sobe, consome o backend. **Commit**

## Fatia 0.4 — Design das Telas (handoff)
**Prompt:**
```
Aplicar ui-design: definir o design system (tokens, tipografia, cores, dark mode) e
gerar mockups HTML das telas principais (Login, Cadastro, Home/Dashboard, Carteira,
Contas, Transferência/PIX, Histórico/Extrato, Notificações, Perfil/Config).
Mapear os fluxos entre telas. Nenhuma lógica; só a referência visual que o
frontend-engineer vai seguir nas fatias seguintes.
```
**Valida:** mockups e design system aprovados. **Commit**

---

# Fase 1 — Identity (Autenticação)

## Fatia 1.1 — Contrato + Domínio
**Prompt:**
```
Architect define o contrato OpenAPI de auth (register, login, refresh) e o agregado
User/Credential com invariantes (domain-driven-design). ADR 003 (auth-security: JWT
stateless + refresh rotativo, MFA opcional). input-validation nos DTOs.
Backend implementa só o domínio. unit-testing cobre. Sem persistência, sem endpoint.
```
**Valida:** unit tests verdes. **Commit**

## Fatia 1.2 — Registro + Login (backend)
**Prompt:**
```
Persistência de User, POST /auth/register e /auth/login, BCrypt, JWT + refresh.
Spring Security stateless. object-mapping DTO<->domínio na borda. Erros como
ProblemDetail (RFC 9457). integration-testing (Testcontainers): fluxo feliz +
credencial inválida.
```
**Valida:** IT verde, login retorna tokens. **Commit**

## Fatia 1.3 — Refresh Token + MFA
**Prompt:**
```
Refresh rotativo com invalidação do anterior (guardar em Redis via caching). MFA
TOTP opcional. Endpoint /auth/refresh. Testes de rotação e de reuso de token
revogado (deve falhar).
```
**Valida:** rotação funciona, reuso barrado. **Commit**

## Fatia 1.4 — Telas de Login e Cadastro (frontend)
**Prompt:**
```
Frontend seguindo o mockup da Fatia 0.4: Login e Cadastro com RHF + Zod, guarda de
rota, persistência de sessão, refresh automático via interceptor, toasts, skeleton.
api-client-typegen gera o client tipado do OpenAPI. frontend-testing no fluxo.
Valide ponta a ponta contra o backend real.
```
**Valida:** login/cadastro ponta a ponta. **Commit**

---

# Fase 2 — Core Banking

## Fatia 2.1 — Contrato + Domínio (Customer, Account, Balance)
**Prompt:**
```
Architect define contrato e agregados Customer, Account, Balance com invariantes
(saldo nunca negativo sem limite, moeda, status). Evento AccountOpened publicado
internamente. Backend implementa só o domínio. unit-testing cobre.
```
**Valida:** unit tests verdes. **Commit**

## Fatia 2.2 — Abertura de Conta + Persistência
**Prompt:**
```
Entidades JPA, repositories, migrations (database-migrations) e caso de uso
OpenAccount. POST /accounts. integration-testing com Testcontainers.
```
**Valida:** conta persistida, IT verde. **Commit**

## Fatia 2.3 — Extrato e Saldo (CQRS leitura)
**Prompt:**
```
Read model de extrato/movimentações (projeção a partir de eventos, CQRS via
distributed-patterns). GET /accounts/{id}/balance e /statement com paginação. Cache
de saldo em Redis (caching) com invalidação por evento. Testes de projeção e de
invalidação.
```
**Valida:** extrato e saldo corretos, cache invalidando. **Commit**

## Fatia 2.4 — Home, Carteira e Contas (frontend)
**Prompt:**
```
Frontend seguindo o mockup: Home/Dashboard com saldo (Recharts), Carteira, listagem
de contas, extrato paginado. Skeleton, erro, dark mode. frontend-accessibility na
navegação. Valide contra o backend real.
```
**Valida:** telas ponta a ponta. **Commit**

---

# Fase 3 — Payments (Diferencial: Saga + Outbox + Idempotência)

## Fatia 3.1 — ADR de Consistência
**Prompt:**
```
Architect cria ADR 004 comparando, para transferência entre contas: transação local
ACID simples; Saga orquestrada + Transactional Outbox; consistência forte via lock.
Prós/contras e escolha Saga + Outbox (justificando pela evolução para eventos
cross-context e extração de módulos). Nenhuma implementação.
```
**Valida:** ADR aprovado. **Commit**

## Fatia 3.2 — Transferência Interna com Outbox
**Prompt:**
```
TransferFunds como Saga: débito na origem, crédito no destino. Transactional Outbox
(messaging-patterns) publica TransferCompleted/TransferFailed no Kafka de forma
atômica com a transação. Idempotency key em POST /transfers (mesma key = mesma
resposta, sem duplo débito). Testes de idempotência e de publicação via outbox.
```
**Valida:** transferência atômica, idempotente, evento publicado. **Commit**

## Fatia 3.3 — Compensação (falha no meio da Saga)
**Prompt:**
```
Passo de compensação: se o crédito falhar após o débito, estornar. Teste que injeta
falha no crédito e prova que o saldo da origem volta ao original e TransferFailed é
emitido.
```
**Valida:** compensação correta, sem dinheiro perdido. **Commit**

## Fatia 3.4 — Concorrência (dois débitos simultâneos) ⭐
**Prompt:**
```
concurrency-control: lock otimista (@Version) na conta. OptimisticLockException ->
ConflictException -> HTTP 409. Teste de concorrência: CountDownLatch +
ExecutorService + N threads debitando a mesma conta com saldo para apenas uma.
Provar que só uma transferência conclui e o saldo nunca fica inconsistente.
```
**Valida:** sem saldo negativo, conflitos tratados. **Commit**

## Fatia 3.5 — Resiliência
**Prompt:**
```
resilience (Resilience4j) nas chamadas de risco: retry com backoff na
publicação/consumo, circuit breaker, bulkhead e rate limiting no endpoint de
transferências. Testes dos comportamentos (circuito abre, retry ocorre, rate limit
barra).
```
**Valida:** padrões de resiliência ativos e testados. **Commit**

## Fatia 3.6 — PIX, TED e Agendamento
**Prompt:**
```
Reusar a Saga para PIX (interno simulado) e TED. Agendamento de pagamento via
background-jobs (@Scheduled/Quartz) com cancelamento. Idempotência mantida. Testes
por tipo e do job de agendamento (dispara na hora certa, cancelamento impede
execução).
```
**Valida:** PIX/TED/agendamento funcionando. **Commit**

## Fatia 3.7 — Transferências e PIX (frontend)
**Prompt:**
```
Frontend seguindo o mockup: fluxo de Transferência e PIX (RHF + Zod), confirmação,
estados de sucesso/erro/conflito (409), histórico. Update otimista + rollback na UI.
frontend-testing incluindo o caso de conflito. Valide ponta a ponta.
```
**Valida:** fluxo completo, conflito tratado na UI. **Commit**

---

# Fase 4 — Notifications (+ Primeira Extração para Microsserviço)

## Fatia 4.1 — Módulo de Notificações (in-process)
**Prompt:**
```
Módulo notifications consome eventos (TransferCompleted, AccountOpened) via Kafka e
simula envio (email/push/SMS) com log. DLQ para falhas (messaging-patterns). Testes
de consumo e DLQ.
```
**Valida:** eventos consumidos, DLQ funciona. **Commit**

## Fatia 4.2 — Extração para Microsserviço ⭐
**Prompt:**
```
Architect cria ADR 005 (extração de notifications para serviço independente).
Extrair o módulo para um serviço Spring Boot separado, comunicando só por Kafka.
docker-compose (containerization) passa a subir os dois serviços. Contrato de evento
versionado. Provar que o core continua funcionando com o serviço separado no ar e
derrubado (notificação não bloqueia a transação).
```
**Valida:** serviço separado, core desacoplado. **Commit**

## Fatia 4.3 — Central de Notificações (frontend)
**Prompt:**
```
Frontend seguindo o mockup: página de Notificações, badge de não lidas, toasts em
tempo quase real. Valide ponta a ponta.
```
**Valida:** notificações na UI. **Commit**

---

# Fase 5 — Fraud (Simulado)

## Fatia 5.1 — Score e Bloqueio
**Prompt:**
```
Módulo fraud avalia transferências por regras simples (valor, frequência, destino
novo) e emite FraudSuspected. Transferência de alto risco entra em revisão em vez de
concluir. Auditoria das decisões. Testes das regras e do bloqueio.
```
**Valida:** transação suspeita bloqueada e auditada. **Commit**

---

# Fase 6 — Observabilidade

## Fatia 6.1 — Métricas, Logs e Tracing
**Prompt:**
```
Devsecops aplica observability: Micrometer + Prometheus (métricas de negócio:
transferências, conflitos, latência p95), Grafana, Loki (logs estruturados
correlacionados), Tempo + OpenTelemetry (tracing distribuído core > kafka >
notifications). Dashboards prontos.
Valide um trace ponta a ponta de uma transferência.
```
**Valida:** métricas, logs e trace visíveis. **Commit**

---

# Fase 7 — E2E e Hardening

## Fatia 7.1 — Testes E2E (Playwright)
**Prompt:**
```
e2e-testing com Playwright: page objects e cobertura do fluxo crítico ponta a ponta
(cadastro > login > abrir conta > transferência > ver extrato > notificação),
incluindo o caminho de conflito (409). Rodar contra a stack via docker-compose.
```
**Valida:** E2E verde no fluxo crítico. **Commit**

## Fatia 7.2 — Carga e Gargalos (opcional)
**Prompt:**
```
performance-testing (k6/Gatling) no endpoint de transferências: medir p95/p99 sob
carga, achar o gargalo. react-performance no frontend (bundle, re-render).
Documentar achados.
```
**Valida:** relatório de carga e otimizações. **Commit**

---

# Fase 8 — Infraestrutura e Deploy

## Fatia 8.1 — Dockerfiles e Compose
**Prompt:**
```
containerization: Dockerfiles multi-stage (core, notifications, frontend) e
docker-compose completo com Postgres, Redis, Kafka, Prometheus, Grafana, Loki, Tempo
e Nginx (reverse proxy). Tudo por env var com fallback. Valide docker compose up
ponta a ponta.
```
**Valida:** stack sobe inteira. **Commit**

## Fatia 8.2 — CI (GitHub Actions)
**Prompt:**
```
cicd: build backend + frontend, unit + integration (Testcontainers) + verify de
módulos + lint do frontend + smoke E2E. Validar pipeline verde.
```
**Valida:** CI verde. **Commit**

---

# Fase 9 — Documentação

## Fatia 9.1 — OpenAPI/Swagger
**Prompt:** `OpenAPI + Swagger UI documentando todos os endpoints.`
**Valida:** Swagger acessível. **Commit**

## Fatia 9.2 — Diagramas C4 + ADRs
**Prompt:**
```
Docs-writer: diagramas C4 (contexto, container, componentes dos módulos) em Mermaid.
Garantir todos os ADRs em docs/adr.
```
**Valida:** diagramas e ADRs presentes. **Commit**

## Fatia 9.3 — README
**Prompt:**
```
Docs-writer cria README profissional: visão geral, arquitetura (monólito modular +
extração), bounded contexts, tecnologias, como executar, testes, Saga/Outbox/
idempotência, teste de concorrência, resiliência, observabilidade, ADRs, C4,
estrutura.
```
**Valida:** README completo, projeto apresentável. **Commit + Push**

---

# Fluxo de Commits
```
0.1 Scaffolding Modulith   0.2 Infra (PG/Redis/Kafka)   0.3 Frontend scaffold   0.4 ui-design mockups
1.1 Identity domínio       1.2 Register/Login           1.3 Refresh/MFA          1.4 Telas auth
2.1 CoreBanking domínio    2.2 Abertura conta           2.3 Extrato/CQRS         2.4 Home/Carteira
3.1 ADR consistência       3.2 Transfer + Outbox        3.3 Compensação
3.4 Concorrência ⭐        3.5 Resiliência              3.6 PIX/TED/agendamento   3.7 Telas transfer
4.1 Notifications módulo   4.2 Extração microsserviço ⭐ 4.3 Central notif
5.1 Fraud
6.1 Observabilidade
7.1 E2E (Playwright)       7.2 Carga (opcional)
8.1 Dockerfiles + Compose  8.2 CI
9.1 Swagger                9.2 C4 + ADRs                9.3 README
```

# Resultado Final (o que você demonstra em entrevista)
- Por que monólito modular em vez de microsserviços prematuros, e como extrair depois.
- Saga + Transactional Outbox + idempotência numa transferência financeira real.
- Como impedir débito duplo/saldo negativo sob concorrência (teste multi-thread).
- CQRS, consistência eventual, eventos entre bounded contexts com Kafka.
- Resiliência (circuit breaker, retry, bulkhead, rate limit).
- Observabilidade completa (métricas de negócio, logs, tracing distribuído).
- Full stack: mockup > contrato > React consumindo REST com estados reais e E2E.
- Docker, CI/CD, C4, ADRs, documentação.

# Dicas de Operação
- Commit ao fim de cada fatia. Revise o trabalho dos agentes antes de aprovar.
- Sempre valide migrations antes de rodar. Cole o resultado real; não presuma sucesso.
- Contexto limpo entre fatias para reduzir tokens e manter foco.
- Se um agente topar decisão de arquitetura, ele escala ao architect antes de seguir.