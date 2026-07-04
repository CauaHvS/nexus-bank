# ADR-005 — Saga Orquestrada + Transactional Outbox para Transferências no Módulo Payments

## Status
Aceito

## Contexto

O módulo Payments precisa transferir dinheiro entre duas contas (possivelmente de
clientes diferentes). A operação envolve dois agregados distintos (`Account` origem e
`Account` destino), ambos residindo no módulo CoreBanking. O módulo Payments não pode
acessar diretamente os internals do CoreBanking: toda interação ocorre pela API pública
do módulo (interfaces declaradas no pacote raiz), conforme ADR-001 e ADR-002.

O sistema precisa garantir quatro propriedades simultaneamente:

1. **Atomicidade**: débito e crédito ocorrem juntos ou nenhum ocorre. Falha parcial
   deve resultar em compensação explícita, não em estado inconsistente silencioso.
2. **Idempotência**: reenvio da mesma requisição (rede, retry do cliente, job de
   reprocessamento) não pode causar duplo débito nem duplo crédito.
3. **Rastreabilidade**: toda transferência tem um ciclo de vida explícito com
   transições de estado observáveis (auditoria, suporte, Fraud).
4. **Desacoplamento e portabilidade**: o design deve funcionar identicamente se o
   módulo Payments for extraído para microsserviço físico no futuro (Fase 4 do
   roadmap, conforme ADR-002). Isso elimina soluções que dependem de transação
   JPA compartilhada com CoreBanking.

A transferência cruza a fronteira de dois agregados dentro de módulos que não
compartilham contexto transacional por design. Qualquer solução que ignore esse
fato vai funcionar no monólito e quebrar na extração.

## Decisão

Adotar **Saga orquestrada + Transactional Outbox** para o fluxo de transferência.

### Fluxo nominal (caminho feliz)

```
Cliente  ->  PaymentsController
              |
              v
         TransferSaga.initiate()
              |-- persiste Transfer (estado: PENDING) + idempotency_key [transação 1]
              |
              v
         CoreBanking.debit(sourceAccountId, amount)     [transação 2 - API pública]
              |-- Account.debit() valida saldo, emite BalanceUpdated
              |
              v
         Outbox.publish(TransferDebitedEvent)            [na mesma transação 2]
              |
              v
         [Relay Outbox -> Kafka: tópico payments.transfer.debited]
              |
              v
         TransferSaga.onDebitConfirmed()                 [consumidor Kafka]
              |
              v
         CoreBanking.credit(targetAccountId, amount)     [transação 3 - API pública]
              |
              v
         Outbox.publish(TransferCompletedEvent)          [na mesma transação 3]
              |
              v
         Transfer.status = COMPLETED                     [transação 3]
```

### Fluxo de compensação (crédito falha)

```
         CoreBanking.credit() lança exceção
              |
              v
         TransferSaga.onCreditFailed()
              |
              v
         CoreBanking.credit(sourceAccountId, amount)    [estorno - transação 4]
              |
              v
         Outbox.publish(TransferCompensatedEvent)       [na mesma transação 4]
              |
              v
         Transfer.status = FAILED                       [transação 4]
```

### Idempotência

A tabela `transfers` tem constraint `UNIQUE (idempotency_key)`. Toda requisição de
transferência chega com um `idempotency_key` gerado pelo cliente. Se o registro já
existe, a saga retorna o estado atual sem re-executar nenhum passo. O cliente recebe
o mesmo resultado que receberia na primeira chamada.

### Transactional Outbox

O evento de domínio é escrito na tabela `outbox_events` dentro da mesma transação
que altera o estado da saga. Um relay job (Spring Scheduler ou Debezium CDC) lê os
eventos não publicados e os envia ao Kafka, marcando-os como publicados. Isso garante
que o evento só é publicado se a transação de banco commitou, eliminando a janela em
que o estado foi persistido mas o evento se perdeu (falha entre `commit` e
`kafkaTemplate.send()`).

### Orquestrador

A `TransferSaga` é a única classe que conhece o protocolo completo da transferência.
Ela coordena as chamadas às APIs públicas do CoreBanking e as transições de estado da
entidade `Transfer`. Não há coreografia implícita: o fluxo é explícito e rastreável
em um único lugar.

## Consequencias Positivas

- **Portabilidade garantida**: quando Payments for extraído para microsserviço, o
  protocolo não muda. O Kafka já é o canal de comunicação; o banco compartilhado
  deixou de ser requisito desde o início.
- **Idempotência por design**: a constraint de `idempotency_key` garante que nenhum
  retry, seja do cliente ou de um job de reprocessamento, cause efeito duplo.
- **Estado explícito e rastreável**: a entidade `Transfer` percorre
  `PENDING -> COMPLETED | FAILED` com timestamp em cada transição. Suporte,
  auditoria e Fraud têm visibilidade total do ciclo de vida.
- **Compensação documentada**: a saga define explicitamente o que acontece quando o
  crédito falha. Não há estado inconsistente silencioso: ou a operação completa, ou
  o estorno é executado e registrado.
- **Outbox garante entrega**: o evento de domínio é atômico com a transação. Mesmo
  que o broker caia, o relay vai reenviar ao restaurar a conectividade. Não há
  janela de perda silenciosa.
- **Fronteiras de módulo respeitadas**: Payments nunca acessa tabelas ou serviços
  internos de CoreBanking. A comunicação é pela API pública e por eventos, conforme
  ADR-002.

## Consequencias Negativas

- **Consistência eventual**: existe uma janela de tempo em que o débito foi executado
  mas o crédito ainda não. Durante essa janela, o saldo da origem reflete o débito
  mas o saldo do destino ainda não reflete o crédito. Para transferências internas
  isso ocorre em milissegundos; para transferências externas (TED, PIX) isso já é
  inerente ao protocolo. A janela é visível no estado `PENDING` da transferência.
- **Complexidade de implementação**: mais artefatos do que uma transação ACID local:
  entidade `Transfer`, tabela `outbox_events`, relay job, consumidor Kafka, handler
  de compensação. Um CRUD simples não precisa disso.
- **Depuração mais trabalhosa**: um evento pode estar na tabela `outbox_events` mas
  ainda não ter sido publicado (relay com atraso, broker indisponível). A causa de
  uma transferência parada em `PENDING` pode estar no relay, no Kafka ou no
  consumidor. Logs e observabilidade do relay são essenciais.
- **Ordenação e reprocessamento**: o consumidor Kafka deve ser idempotente. Se um
  evento for consumido duas vezes (rebalance, retry), o handler de crédito não pode
  creditar novamente. Mitigação: a saga verifica o estado atual de `Transfer` antes
  de executar qualquer passo.

## Alternativas Consideradas

### Opção 1: Transação ACID local única

Débito e crédito na mesma transação JPA, chamando diretamente os repositórios de
`Account` dentro do mesmo contexto transacional.

**Prós**: simples, sem estados intermediários, sem código de compensação.

**Contras**:
- Viola a fronteira de módulo: Payments acessa diretamente o repositório interno de
  CoreBanking, criando acoplamento estrutural que o `ApplicationModules.verify()` vai
  rejeitar.
- Não funciona após a extração: se Payments virar microsserviço, não há transação
  JPA compartilhada. Todo o código de transferência precisaria ser reescrito.
- Nenhum registro de estado da transferência: sem entidade `Transfer`, não há como
  auditar, rastrear ou expor o histórico de transferências para o cliente.
- Falhas parciais invisíveis: se o crédito falhar após o commit do débito (cenário
  improvável mas possível com exceções de constraint), o estado fica inconsistente
  sem compensação.

**Descartado**: viola ADR-001 e ADR-002 e não sobrevive à extração do módulo.

### Opção 2: Saga orquestrada + Transactional Outbox (esta decisão)

Descrito em detalhe acima. **Escolhido.**

### Opção 3: Consistência forte via SELECT FOR UPDATE (lock pessimista)

As duas contas são bloqueadas com `SELECT FOR UPDATE` antes da operação. O débito
e o crédito ocorrem dentro da mesma transação, com os dois registros locados.

**Prós**: sem estados intermediários, sem código de compensação, fácil de entender.

**Contras**:
- **Deadlock potencial**: se duas transferências A->B e B->A ocorrem simultaneamente,
  cada uma adquire o lock de uma conta e espera pela outra. Deadlock clássico de lock
  ordering. Mitigável com ordenação determinística dos locks (sempre bloquear pelo ID
  menor primeiro), mas adiciona complexidade não óbvia.
- **Não escala**: locks pessimistas em linhas de banco bloqueiam toda operação
  concorrente na mesma conta. Em carga alta, o tempo médio de espera cresce
  linearmente com a fila de operações por conta.
- **Não funciona após extração**: a mesma razão da Opção 1. Sem banco compartilhado,
  não há `SELECT FOR UPDATE` que funcione entre serviços.
- **Não resolve idempotência**: o lock protege contra race condition mas não impede
  que a mesma requisição seja processada duas vezes em momentos diferentes (retry
  após timeout, por exemplo).

**Descartado**: não é portável e não escala.

## Referências

- ADR-002 — Monólito Modular em vez de Microsserviços (regras de comunicação entre módulos)
- ADR-004 — CQRS Leve e Read Model no Módulo Core Banking (API pública de débito/crédito)
- `docs/architecture/payments-events.md` — contratos dos eventos publicados por esta saga
