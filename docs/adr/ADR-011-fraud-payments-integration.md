# ADR-011 — Integração Fraud-Payments: Síncrona In-Process via Interface Pública

## Status
Aceito

## Contexto

A Fase 5 entrega o módulo Fraud, que avalia transferências por regras de risco antes
de permitir o débito. O problema arquitetural é: em que ponto do fluxo de pagamento
o Fraud intervém, e como os dois módulos se comunicam?

O fluxo existente em `InitiateTransferUseCase` é:
1. Verificar idempotência
2. Verificar ownership da conta de origem (`CoreBankingApi.isOwner`)
3. Persistir transferência como PENDING
4. Debitar conta de origem (`CoreBankingApi.debit`)
5. Persistir evento no Outbox

A avaliação de fraude precisa ocorrer antes do passo 4. Se ocorrer depois, o dinheiro
já saiu da conta antes da decisão — comportamento inaceitável para bloqueio.

Três opções foram consideradas:

**Opção A / C — Síncrona in-process:** Payments chama `FraudApi.evaluate()` diretamente
antes do débito. A avaliação ocorre na mesma transação.

**Opção B — Assíncrona via Kafka:** Payments publica `TransferInitiated` no Outbox.
Fraud consome o evento, avalia, publica `FraudDecision` de volta. Payments tem um
listener que muda o status.

## Decisão

Integração síncrona in-process (Opção C), com comunicação exclusivamente pelas
interfaces públicas dos módulos: `FraudApi` (porta de entrada do Fraud) e
`PaymentsApi` (porta pública do Payments, criada para esta fase).

## Justificativa

### Por que não Opção B (Kafka assíncrono)

A opção assíncrona exigiria:
- Inversão do fluxo de `InitiateTransferUseCase`: persistir como PENDING sem debitar,
  publicar evento, aguardar resposta assíncrona.
- Um novo estado transitório (ex.: AWAITING_FRAUD_CHECK) para transferências que ainda
  não foram avaliadas.
- Mecanismo de correlação e timeout: o que acontece se o Fraud nunca responder?
- Compensação adicional: a conta já foi debitada antes? Não, mas o estado fica
  inconsistente sem mecanismo de expiração.

Esse custo é injustificado num monólito onde os dois módulos rodam no mesmo processo.
O benefício real da assincronia (desacoplamento de falhas, resiliência) não se
materializa quando a falha do Fraud é uma falha do próprio processo.

### Por que Opção C

- Simplicidade: a chamada síncrona é mais fácil de raciocinar, testar e manter.
- Atomicidade: a auditoria da avaliação é persistida na mesma transação da
  decisão de débito ou UNDER_REVIEW. Não há janela de inconsistência.
- Conformidade com Spring Modulith: chamada direta pela interface pública é o
  mecanismo de integração documentado para módulos in-process.
- Testabilidade: `FraudApi` pode ser mockada nos testes de `InitiateTransferUseCase`
  sem setup de Kafka ou containers adicionais.

### Dependência bidirecional aceita

O Payments chama `FraudApi` (avaliacao inicial). O Fraud chama `PaymentsApi`
(aprovacao e rejeicao de revisao manual). Essa dependência bidirecional entre módulos
é incomum e vale a nota explícita.

O Spring Modulith não proíbe ciclos entre módulos — ele proíbe apenas acesso a
internals. Ambas as direções de comunicação ocorrem por interfaces públicas
(`FraudApi` e `PaymentsApi`), o que o `ApplicationModules.verify()` aceita.

Alternativa considerada para eliminar o ciclo: o Fraud Controller poderia chamar
diretamente os use cases do Payments via chamada HTTP interna (localhost). Descartado
porque adiciona latência de rede desnecessária e complica o rastreamento transacional
— seria over-engineering sem benefício no monólito.

## Consequências Positivas

- Débito nunca ocorre para transferências BLOCKED ou SUSPICIOUS.
- Auditoria de toda avaliação (APPROVED, SUSPICIOUS, BLOCKED) persistida atomicamente.
- Testes unitários do domínio Fraud são completamente isolados (sem Spring, sem banco).
- Testes de integração de Payments mockam `FraudApi` — mesma mecânica já usada com
  `CoreBankingApi`.
- Custo de extração do Fraud para microsserviço é localizado: apenas a implementação
  de `FraudApi` no módulo Payments muda (vira cliente HTTP com timeout). O domínio
  de ambos os módulos não é tocado.

## Consequências Negativas

- Dependência bidirecional entre Payments e Fraud. Se um dos módulos for extraído,
  a outra direção da dependência precisa virar HTTP também. O custo é conhecido e
  documentado.
- A avaliação de fraude está no caminho crítico da transação. Uma lentidão no Fraud
  impacta a latência de `POST /transfers`. Mitigação: as regras atuais são puramente
  computacionais (sem I/O no caminho crítico de scoring), apenas as queries de contexto
  (`countTransfersLast24h`, `isNewDestination`) adicionam latência de banco.

## Alternativas Consideradas

| Alternativa | Motivo da rejeicao |
|---|---|
| Opção B (Kafka assíncrono) | Complexidade desproporcional ao contexto. Estado transitório adicional. Timeout sem tratamento definido. |
| HTTP interno (localhost) | Latência de rede desnecessária dentro do mesmo processo. Perde atomicidade transacional. |
| Avaliação pós-débito com estorno se BLOCKED | O usuário teria o saldo debitado temporariamente. Experiência ruim e complexidade de compensação adicional. |
