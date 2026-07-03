# CLAUDE.md — Contexto do Projeto

> Lido automaticamente pelo Claude Code. Define como trabalhar neste projeto.

## Identidade
- Nome: distributed-bank
- Domínio: banco digital distribuído (Core Banking, Payments, Cards, Identity, Notifications, Fraud)
- Stack backend: Java 21, Spring Boot 3, Spring Modulith, PostgreSQL, Kafka, Redis, Flyway
- Stack frontend: React + TypeScript, Vite, TailwindCSS, TanStack Query, React Hook Form + Zod, Axios, Recharts
- Arquitetura: **Monólito modular** (Spring Modulith). Cada bounded context é um módulo, hexagonal por dentro. Comunicação entre módulos por eventos (application events + Kafka onde faz sentido). No fim do roadmap, **extração seletiva** de UM módulo (Notifications ou Fraud) para microsserviço físico, para demonstrar decomposição.
- Objetivo: portfólio sênior simulando produção

### Decisão de arquitetura (importante)
Este projeto NÃO nasce como 7 microsserviços. Isso é deliberado. Microsserviços
físicos desde o início, solo, produzem um distributed monolith: todo o custo
operacional, nenhum benefício real. A senioridade está em saber quando não dividir.
O monólito modular exercita os mesmos padrões distribuídos (Saga, Outbox, CQRS,
consistência eventual, idempotência) dentro de um deployable, e a extração final
prova a capacidade de decompor. Ver ADR 001 e ADR 002.

### Escopo (v1)
Núcleo: Identity, Core Banking, Payments (com Saga/Outbox), Notifications, Fraud
(simulado) + frontend. **Investments e Cards-faturas ficam para v2.** Não implemente
o que está fora de escopo sem escalar.

## Como você (Claude) deve trabalhar aqui

### Idioma e tom
Português brasileiro, conciso e direto. Sem em-dashes. Honestidade técnica: aponte
problemas, riscos e trade-offs; discorde com fundamento; nunca concorde só pra agradar.

### Método
- Conceito antes de mudanças significativas.
- Fatias verticais: feature ponta a ponta (contrato > backend > frontend > testes).
- Contract First: contrato da API (OpenAPI) definido antes de backend e frontend.
- Valide cada etapa (compile, rode, teste) antes de avançar. O humano cola o
  resultado real (log, erro, print, response) e você confirma antes de seguir.
- Ao diagnosticar erro: leia o log de baixo pra cima, ache a causa raiz, distinga
  sintoma de causa, explique antes de corrigir. Teste que passa isolado mas falha
  na suíte = flaky (estado compartilhado), torne determinístico.

### Decisões
- Trade-offs: opções com prós/contras + recomendação, mas o humano decide.
- Toda decisão relevante vira ADR (docs/adr/): Contexto, Decisão, Consequências
  Positivas e Negativas, Alternativas Consideradas.
- Se um agente de implementação topar uma decisão de arquitetura, para e escala
  ao architect.

### Qualidade de código
- Identificadores (classes, métodos, variáveis, pacotes) em inglês.
- Contratos técnicos (campos JSON, enums, chaves) em inglês.
- Mensagens ao usuário, logs, comentários, mensagens de erro em português.
- ADRs e documentação em português.
- Domínio sem framework. Regras de negócio isoladas da infra.
- Sem código morto, duplicação ou over-engineering. Config por env var com
  fallback; nunca hardcode segredos.
- Testes, casos de borda e erro desde o início.

### Fronteiras de módulo (Spring Modulith)
- Um módulo por bounded context. Módulo NÃO acessa internals de outro módulo.
- Comunicação entre módulos: por eventos de domínio, não por chamada direta a
  serviços internos de outro módulo. API pública do módulo é explícita.
- Teste de arquitetura (ApplicationModules.verify) roda no CI. Violação quebra build.

### Definição de "pronto"
Feature pronta com: contrato OpenAPI, backend + frontend integrados, testes (unit +
integração + concorrência onde aplicável), observabilidade, segurança,
containerização e documentação.

## Orquestração com subagentes

Delegue para o agente certo em vez de fazer tudo no contexto principal:

- **architect** — módulos, fronteiras, padrões, ADRs, contratos. Início de feature.
- **backend-engineer** — implementação backend (Java/Spring).
- **frontend-engineer** — implementação frontend (React/TS).
- **test-engineer** — testes (unit + integração + concorrência). Após implementar.
- **code-reviewer** — revisão antes do commit.
- **devsecops** — Docker, CI/CD, observabilidade, segurança de infra.
- **docs-writer** — README, ADRs, diagramas (C4, Mermaid).

Fluxo típico de uma fatia vertical:
1. architect define contrato da API e evento(s) de domínio (+ ADR se houver decisão).
2. backend-engineer implementa (domínio > aplicação > adapters).
3. frontend-engineer consome o contrato (página + estados de loading/erro).
4. test-engineer escreve e roda os testes.
5. code-reviewer revisa.
6. devsecops garante que roda e é seguro.
7. docs-writer atualiza a documentação.
Você (orquestrador) coordena e reporta ao humano em alto nível.

## Comandos do projeto
```
# Backend: build e testes
./mvnw clean verify
# Frontend
cd frontend && npm run dev
cd frontend && npm run build && npm run test
# Subir infra + app
docker compose up -d --build
# Só infra (dev local)
docker compose up -d postgres kafka redis
```

## Skills disponíveis
Carregadas sob demanda de ~/.claude/skills/ (global) e .claude/skills/ (local).
Relevantes para este projeto:

Planejamento: project-planning, adr-writer, event-storming, version-troubleshooting.

Backend/arquitetura: hexagonal-architecture, modular-monolith, domain-driven-design,
api-design, input-validation, object-mapping, auth-security, database-migrations,
concurrency-control, messaging-patterns, distributed-patterns (Saga, Outbox, CQRS,
consistência eventual), resilience (circuit breaker, retry, bulkhead, time limiter),
caching, background-jobs, secrets-management.

Frontend: ui-design, react-frontend, api-client-typegen, frontend-accessibility,
react-performance.

Testes: unit-testing, integration-testing, frontend-testing, e2e-testing,
performance-testing.

Infra/observabilidade: observability, containerization, cicd, kubernetes-helm (v2).