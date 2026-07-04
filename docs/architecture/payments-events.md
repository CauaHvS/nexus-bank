# Contratos de Eventos de Domínio — Módulo Payments

> Documento de referência para a implementação da Saga de transferência.
> Toda mudança de schema deve passar por revisão do architect antes de ser aplicada.
> Ver ADR-005 para a decisão de arquitetura que origina estes eventos.

## Visão Geral

A saga de transferência publica quatro eventos ao longo do ciclo de vida de uma
operação. Todos são escritos via Transactional Outbox (tabela `outbox_events`) e
entregues ao Kafka pelo relay. O consumidor principal é o próprio módulo Payments
(para avançar o estado da saga); consumidores secundários são Notifications e Fraud.

```
TransferInitiated  -->  [PENDING]
       |
       v (débito OK)
TransferDebited    -->  [DEBITING -> aguarda crédito]   (evento interno, não listado aqui)
       |
       v (crédito OK)
TransferCompleted  -->  [COMPLETED]
       |
       v (crédito falhou, estorno executado)
TransferFailed + TransferCompensated  -->  [FAILED]
```

---

## Evento: TransferInitiated

Publicado imediatamente após a criação da transferência no estado `PENDING`.
Sinaliza que a saga foi iniciada e os passos de débito/crédito ainda vão ocorrer.

**Topico Kafka:** `payments.transfer.initiated`

**Quem publica:** `TransferSaga` (módulo Payments), via Outbox, na mesma transação
que persiste a entidade `Transfer`.

**Quem consome:**
- `FraudModule` — avalia o risco da operação antes ou durante a execução.
- `NotificationsModule` — pode notificar o usuário que a transferência foi iniciada
  (opcional, dependendo da preferência de notificação).

**Schema:**

| Campo            | Tipo          | Obrigatorio | Descricao                                                                 |
|------------------|---------------|-------------|---------------------------------------------------------------------------|
| `transferId`     | `UUID`        | sim         | Identificador unico da transferencia gerado pela saga                     |
| `sourceAccountId`| `UUID`        | sim         | Conta de origem do debito                                                 |
| `targetAccountId`| `UUID`        | sim         | Conta de destino do credito                                               |
| `amount`         | `BigDecimal`  | sim         | Valor da transferencia. Positivo, maior que zero. Precisao: 2 decimais   |
| `currency`       | `String`      | sim         | Codigo ISO 4217 da moeda (ex: `BRL`). 3 caracteres maiusculos            |
| `idempotencyKey` | `String`      | sim         | Chave de idempotencia fornecida pelo cliente. Unica na tabela `transfers` |
| `initiatedAt`    | `Instant`     | sim         | Timestamp UTC de criacao da transferencia                                 |

**Exemplo:**

```json
{
  "transferId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sourceAccountId": "11111111-0000-0000-0000-000000000001",
  "targetAccountId": "22222222-0000-0000-0000-000000000002",
  "amount": "250.00",
  "currency": "BRL",
  "idempotencyKey": "cli-req-2026-07-04-xyz",
  "initiatedAt": "2026-07-04T10:00:00Z"
}
```

---

## Evento: TransferCompleted

Publicado quando débito e crédito foram executados com sucesso. A entidade
`Transfer` transita para `COMPLETED`.

**Topico Kafka:** `payments.transfer.completed`

**Quem publica:** `TransferSaga` (módulo Payments), via Outbox, na mesma transação
que executa o crédito e atualiza o estado para `COMPLETED`.

**Quem consome:**
- `NotificationsModule` — notifica origem e destino sobre a transferencia concluida.
- `FraudModule` — encerra a avaliacao de risco associada a esta transferencia.

**Schema:**

| Campo            | Tipo         | Obrigatorio | Descricao                                                        |
|------------------|--------------|-------------|------------------------------------------------------------------|
| `transferId`     | `UUID`       | sim         | Identificador da transferencia (mesmo valor de `TransferInitiated`) |
| `sourceAccountId`| `UUID`       | sim         | Conta de origem                                                  |
| `targetAccountId`| `UUID`       | sim         | Conta de destino                                                 |
| `amount`         | `BigDecimal` | sim         | Valor transferido. Igual ao valor de `TransferInitiated`         |
| `currency`       | `String`     | sim         | Codigo ISO 4217 da moeda                                         |
| `completedAt`    | `Instant`    | sim         | Timestamp UTC de conclusao (credito executado)                   |

**Exemplo:**

```json
{
  "transferId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sourceAccountId": "11111111-0000-0000-0000-000000000001",
  "targetAccountId": "22222222-0000-0000-0000-000000000002",
  "amount": "250.00",
  "currency": "BRL",
  "completedAt": "2026-07-04T10:00:02Z"
}
```

---

## Evento: TransferFailed

Publicado quando a saga encerrou com falha e a compensação foi executada (ou
tentada). A entidade `Transfer` transita para `FAILED`. Este evento é publicado
em conjunto com `TransferCompensated` quando o estorno foi bem-sucedido.

**Topico Kafka:** `payments.transfer.failed`

**Quem publica:** `TransferSaga` (módulo Payments), via Outbox, na mesma transação
que registra o estado `FAILED` e o resultado da compensação.

**Quem consome:**
- `NotificationsModule` — notifica o usuario de origem que a transferencia falhou.
- `FraudModule` — registra o evento de falha para analise de padroes.

**Schema:**

| Campo          | Tipo      | Obrigatorio | Descricao                                                                          |
|----------------|-----------|-------------|------------------------------------------------------------------------------------|
| `transferId`   | `UUID`    | sim         | Identificador da transferencia                                                     |
| `reason`       | `String`  | sim         | Codigo de falha legivel por maquina. Valores: `INSUFFICIENT_FUNDS`, `ACCOUNT_NOT_FOUND`, `ACCOUNT_INACTIVE`, `CREDIT_FAILED`, `COMPENSATION_FAILED`, `UNKNOWN` |
| `failedAt`     | `Instant` | sim         | Timestamp UTC do momento em que a saga registrou a falha                          |

**Exemplo:**

```json
{
  "transferId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "reason": "INSUFFICIENT_FUNDS",
  "failedAt": "2026-07-04T10:00:01Z"
}
```

**Nota sobre `COMPENSATION_FAILED`**: se o débito foi executado mas o estorno
subsequente também falhou, o estado é `COMPENSATION_FAILED`. Esse cenário exige
intervenção manual e deve disparar alerta de alta prioridade no sistema de
observabilidade. A entidade `Transfer` fica em estado `FAILED` com flag
`compensationFailed = true` para rastreabilidade.

---

## Evento: TransferCompensated

Publicado quando o estorno do débito foi executado com sucesso após uma falha no
crédito. Sinaliza que o saldo da conta de origem foi restaurado.

**Topico Kafka:** `payments.transfer.compensated`

**Quem publica:** `TransferSaga` (módulo Payments), via Outbox, na mesma transação
que executa o crédito de estorno na conta de origem.

**Quem consome:**
- `NotificationsModule` — pode complementar a notificacao de falha com confirmacao
  de que o valor foi estornado.
- `FraudModule` — registra o estorno para analise de padroes.

**Schema:**

| Campo             | Tipo         | Obrigatorio | Descricao                                                     |
|-------------------|--------------|-------------|---------------------------------------------------------------|
| `transferId`      | `UUID`       | sim         | Identificador da transferencia compensada                     |
| `sourceAccountId` | `UUID`       | sim         | Conta de origem que recebeu o estorno                         |
| `amountReturned`  | `BigDecimal` | sim         | Valor devolvido a conta de origem. Igual ao valor do debito   |
| `compensatedAt`   | `Instant`    | sim         | Timestamp UTC de execucao do estorno                          |

**Exemplo:**

```json
{
  "transferId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sourceAccountId": "11111111-0000-0000-0000-000000000001",
  "amountReturned": "250.00",
  "compensatedAt": "2026-07-04T10:00:03Z"
}
```

---

## Tabela Resumo

| Evento                  | Topico Kafka                        | Publica          | Consome                          | Gatilho                              |
|-------------------------|-------------------------------------|------------------|----------------------------------|--------------------------------------|
| `TransferInitiated`     | `payments.transfer.initiated`       | Payments (Saga)  | Fraud, Notifications             | Saga iniciada, Transfer = PENDING    |
| `TransferCompleted`     | `payments.transfer.completed`       | Payments (Saga)  | Notifications, Fraud             | Debito + credito OK, Transfer = COMPLETED |
| `TransferFailed`        | `payments.transfer.failed`          | Payments (Saga)  | Notifications, Fraud             | Saga encerrada com erro              |
| `TransferCompensated`   | `payments.transfer.compensated`     | Payments (Saga)  | Notifications, Fraud             | Estorno executado apos falha no credito |

---

## Convencoes de Implementacao

### Envelope padrao dos eventos

Todos os eventos publicados via Outbox seguem o envelope:

```json
{
  "eventId": "<UUID gerado no momento da publicacao>",
  "eventType": "<nome do evento, ex: TransferCompleted>",
  "occurredAt": "<Instant UTC>",
  "payload": { ... }
}
```

O campo `eventId` garante deduplicacao no consumidor: o handler verifica se ja
processou aquele `eventId` antes de executar qualquer efeito colateral.

### Convencao de nomenclatura dos topicos

`<dominio>.<agregado>.<evento-em-kebab-case>`

Exemplos: `payments.transfer.initiated`, `payments.transfer.completed`.

### Retencao

Todos os topicos do modulo Payments devem ser configurados com retencao minima
de 7 dias para suportar reprocessamento e auditoria.

### Garantia de entrega

Configuracao minima recomendada no produtor Kafka:
- `acks=all` — confirma escrita em todos os replicas do ISR.
- `enable.idempotence=true` — elimina duplicatas de producao em caso de retry.
- `retries=Integer.MAX_VALUE` com `delivery.timeout.ms` configurado.

O consumidor deve usar `enable.auto.commit=false` e commitar offset somente apos
processamento bem-sucedido, com idempotencia no handler (verificacao por `eventId`).
