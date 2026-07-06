# ADR-007 — Estratégia de DLQ para o Módulo Notifications

## Status
Aceito

## Contexto

O módulo Notifications consome eventos do Kafka (`payments.transfer.completed`,
`payments.transfer.failed`, `corebanking.account.opened`). Uma mensagem pode falhar
ao ser processada por:

- Erro temporário de banco (conexão, timeout)
- Payload malformado ou versionamento incompatível
- Bug na lógica de criação de notificação

Sem uma estratégia de tratamento de falha, o consumidor Kafka para em dead-lock
(o offset não avança e o tópico fica bloqueado) ou descarta a mensagem silenciosamente.

Na Fatia 4.2 o módulo será extraído para microsserviço físico. A estratégia escolhida
precisa funcionar identicamente em ambos os contextos (in-process e serviço separado).

### Opções avaliadas

**Opção A — `@RetryableTopic` do Spring Kafka (DLQ automática)**

- Prós: zero boilerplate; retry com backoff configurável por anotação; DLQ em tópico
  Kafka separado (`*.DLT`); mensagens DLQ podem ser reprocessadas com ferramentas
  padrão (Kafka console consumer, Redpanda Console).
- Contras: cria tópicos de retry intermediários no Kafka (`*.RETRY-0`, `*.RETRY-1`
  etc.), aumentando a complexidade operacional; reprocessamento da DLT requer
  consumer separado ou intervenção manual; difícil inspecionar o estado em ambiente
  de desenvolvimento sem UI Kafka.

**Opção B — Retry manual + tabela `notification_dlq` no PostgreSQL**

- Prós: DLQ visível via SQL (sem dependência de UI Kafka); pode ser inspecionada e
  reprocessada com queries simples; consistente com o padrão Outbox já adotado no
  módulo Payments (gravar no banco antes de qualquer side-effect externo); sem tópicos
  Kafka extras; o reprocessamento pode ser feito por um scheduler simples.
- Contras: mais código para escrever (catch, inserção na tabela, scheduler de
  reprocessamento); o scheduler de reprocessamento precisa de cuidado com idempotência.

## Decisão

Adotar **Opção B — retry manual com tabela `notification_dlq`**.

### Justificativa

1. **Consistência com o projeto**: o módulo Payments já usa Outbox (banco como buffer
   confiável). Manter o mesmo padrão reduz a superfície operacional.
2. **Extração futura (Fatia 4.2)**: a tabela `notification_dlq` pertence ao schema
   `notifications` do PostgreSQL. Quando o módulo for extraído, o banco vai junto —
   a estratégia de DLQ não muda.
3. **Visibilidade de desenvolvimento**: consultar `SELECT * FROM notifications.notification_dlq`
   é mais direto do que navegar em tópicos Kafka de DLT durante desenvolvimento local.
4. **Escala**: o volume de notificações falhas esperado é baixo. O overhead de uma
   tabela extra não é relevante neste contexto.

### Comportamento definido

```
1. Consumidor Kafka recebe mensagem.
2. Tenta processar (criar a Notification e persistir).
3. Se falhar: aguarda 500ms e tenta novamente (máximo 3 tentativas).
4. Se esgotar as tentativas: grava em notification_dlq (topic, payload, erro, retry_count).
5. Offset é comitado — o tópico não trava.
6. Um scheduler (@Scheduled, fixedDelay=60s) tenta reprocessar os registros da DLQ
   com retry_count < MAX_DLQ_RETRIES (ex: 5). Se falhar novamente, incrementa
   retry_count. Se atingir MAX_DLQ_RETRIES, loga alerta e para de tentar (requer
   intervenção manual).
```

## Consequências positivas

- Kafka não trava em caso de falha persistente.
- DLQ auditável e reprocessável via SQL.
- Estratégia portátil para microsserviço físico.
- Comportamento de retry configurável sem redeployar (MAX_DLQ_RETRIES por env var).

## Consequências negativas

- Requer scheduler adicional para reprocessamento da DLQ.
- O scheduler precisa ser idempotente (a criação de Notification usa notificationId
  como chave de idempotência para evitar duplicatas).
- Mais código que `@RetryableTopic`.

## Alternativas consideradas

Ver Opção A acima.
