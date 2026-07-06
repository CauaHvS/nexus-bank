# ADR-008 — Entrega do Evento AccountOpened para o Módulo Notifications

## Status
Aceito

## Contexto

O módulo CoreBanking publica `AccountOpened` via `ApplicationEventPublisher` do Spring
(evento in-process). O módulo Payments publica os seus eventos via Transactional Outbox
no Kafka. O módulo Notifications precisa reagir ao `AccountOpened` para criar a
notificação "Sua conta foi aberta com sucesso".

### Assimetria atual

Os eventos de Payments chegam via Kafka (Outbox). O `AccountOpened` chega via
`ApplicationEventPublisher` — somente enquanto o monólito existir inteiro.

Na Fatia 4.2, quando Notifications for extraído para microsserviço físico, esse
consumo in-process deixa de funcionar. O módulo precisará receber `AccountOpened`
via Kafka de qualquer forma.

### Opções avaliadas

**Opção A — Consumir `AccountOpened` in-process via `@ApplicationModuleListener`
(Fatia 4.1) e via Kafka (Fatia 4.2)**

Notifications ouve o evento Spring durante a Fatia 4.1. Na Fatia 4.2, troca o
`@ApplicationModuleListener` por um `@KafkaListener` após o CoreBanking passar a
publicar o evento via Outbox.

- Prós: sem mudança no CoreBanking agora; funciona imediatamente.
- Contras: a Fatia 4.1 fica com um caminho de entrega diferente dos outros dois
  eventos (Payments via Kafka, CoreBanking via in-process). Isso cria divergência
  que precisa ser resolvida na 4.2 — mais trabalho naquele momento.

**Opção B — CoreBanking publica `AccountOpened` via Outbox + Kafka desde a Fatia 4.1**

Adicionar Outbox ao CoreBanking para `AccountOpened` antes de implementar o listener
em Notifications. Notifications só consome Kafka, sem caminho in-process.

- Prós: Notifications tem um único mecanismo de entrada desde o início (Kafka);
  extração na Fatia 4.2 não exige nenhuma mudança no listener.
- Contras: requer trabalho adicional no CoreBanking (Outbox, migration, serialização);
  aumenta o escopo da Fatia 4.1.

**Opção C — Notifications ouve `AccountOpened` in-process para sempre (sem Kafka)**

Só funciona no monólito. Incompatível com a Fatia 4.2.

Descartada imediatamente.

## Decisão

Adotar **Opção B**: o CoreBanking adiciona suporte a Outbox para o evento `AccountOpened`
publicando no tópico `corebanking.account.opened`. O módulo Notifications consume
exclusivamente via Kafka desde a Fatia 4.1.

### Justificativa

1. **Consistência**: todos os eventos consumidos por Notifications chegam pelo mesmo
   canal (Kafka). O `KafkaNotificationListener` tem um único ponto de entrada.
2. **Sem retrabalho**: a Fatia 4.2 não precisa trocar o mecanismo de entrega do
   `AccountOpened` — o listener já está correto.
3. **Custo do CoreBanking é baixo**: o Outbox e o OutboxPoller já existem no módulo
   Payments e na infra transversal. O CoreBanking precisa apenas de uma tabela
   `corebanking.outbox` e de chamar o repositório de outbox ao publicar `AccountOpened`.
   O `OutboxPoller` já resolve o tópico via `resolveTopicFor` — basta adicionar o
   case `AccountOpened -> "corebanking.account.opened"`.

### Tópicos Kafka consumidos por Notifications

| Tópico                          | Evento de origem  | Módulo produtor |
|---------------------------------|-------------------|-----------------|
| `payments.transfer.completed`   | TransferCompleted | Payments        |
| `payments.transfer.failed`      | TransferFailed    | Payments        |
| `corebanking.account.opened`    | AccountOpened     | CoreBanking     |

### Formato do payload em `corebanking.account.opened`

O payload segue o mesmo padrão do Outbox de Payments: JSON com os campos do evento
de domínio. O backend-engineer deve serializar `AccountOpened` com Jackson ao
gravar no outbox do CoreBanking:

```json
{
  "accountId": "uuid",
  "customerId": "uuid",
  "accountNumber": "0001-1",
  "type": "CHECKING",
  "currency": "BRL",
  "occurredAt": "2026-07-05T14:00:00Z"
}
```

O `userId` para criar a notificação é o `customerId` do evento (os dois são o mesmo
identificador no contexto deste sistema — CustomerId == UserId do módulo Identity).

## Consequências positivas

- Notifications nunca depende de `ApplicationEventPublisher` do Spring: pronto para extração.
- Canal único de entrada simplifica o `KafkaNotificationListener`.
- Outbox do CoreBanking oferece a mesma garantia at-least-once do Payments.

## Consequências negativas

- Aumenta o escopo do CoreBanking na Fatia 4.1 (migration + OutboxRepository + serialização).
- O `OutboxPoller` na infra transversal precisa ser estendido para suportar eventos
  de múltiplos módulos produtores. Alternativa: cada módulo tem seu próprio poller
  (mais isolamento, mais duplicação de código — decisão delegada ao backend-engineer).

## Alternativas consideradas

Ver Opção A e Opção C acima.
