# ADR-006 — Origem da conta debitada e verificacao de ownership em transferencias

**Status:** Aceito
**Data:** 2026-07-05
**Modulo:** Payments

---

## Contexto

O endpoint `POST /transfers` precisa saber qual conta debitar. O JWT emitido pelo
modulo Identity contem apenas o `userId` (subject do token). Ha duas opcoes para
fornecer o `sourceAccountId`:

- **Opcao A:** Derivar automaticamente do JWT (o servidor infere a conta a partir
  do userId).
- **Opcao B:** Receber `sourceAccountId` explicito no corpo da requisicao.

Alem disso, independentemente de qual opcao for escolhida, e necessario decidir
onde e como verificar que a conta de origem pertence ao usuario autenticado
(verificacao de ownership).

---

## Decisao

**Opcao B e adotada:** `sourceAccountId` e campo obrigatorio no corpo da requisicao.

A verificacao de ownership e responsabilidade da **camada de aplicacao** (use case
`InitiateTransferUseCase`), nao do controller. O controller passa o `authenticatedUserId`
no command; o use case verifica antes de debitar.

---

## Justificativa

### Por que Opcao B e nao Opcao A

Um usuario pode ter multiplas contas (corrente + poupanca). Inferir automaticamente
qual conta debitar exigiria uma regra arbitraria no servidor (ex.: sempre a conta
corrente) ou uma query adicional para listar contas e escolher. Ambas sao piores:

- A primeira esconde informacao do usuario e quebra o caso de uso de transferencia
  a partir da poupanca.
- A segunda adiciona latencia e logica de selecao que pertence ao frontend (que
  tem contexto de qual conta o usuario selecionou na UI).

O JWT nao deve carregar `accountId` porque: (a) accountId e artefato de
corebanking, nao de identidade; (b) forcaria re-emissao do token a cada abertura
de nova conta; (c) violaria o principio de que o token carrega apenas claims de
autenticacao/autorizacao, nao estado de recurso.

### Por que a verificacao de ownership pertence ao use case

O controller e adaptador de entrada: ele traduz HTTP para comando de aplicacao.
Verificar se uma conta pertence a um usuario e regra de autorizacao de negocio,
nao validacao de formato. Coloca-la no controller violaria a separacao de camadas
(adapter nao deve conter regras de negocio). O use case possui acesso ao
`CoreBankingApi` que pode realizar a verificacao de forma coesa com o debito,
dentro da mesma transacao.

---

## Consequencias positivas

- Frontend seleciona a conta de origem explicitamente: UX clara e sem ambiguidade.
- A mesma API funciona para qualquer numero de contas por usuario sem mudancas.
- Ownership verificado antes do debito, dentro da transacao: sem janela de TOCTOU.
- JWT permanece limpo: apenas claims de identidade.

## Consequencias negativas

- Frontend precisa conhecer o `accountId` antes de iniciar a transferencia (mas
  ja o tem: listou as contas do usuario no dashboard, o que e fluxo obrigatorio
  de qualquer tela de transferencia).
- Uma chamada adicional ao `CoreBankingApi.isOwner` e necessaria por transferencia
  (custo aceitavel: e uma leitura por chave primaria, sem impacto de performance).

---

## Alternativas consideradas

### Opcao A — Derivar sourceAccountId do userId no servidor

**Vantagem:** request body menor; o frontend nao precisa enviar accountId.
**Desvantagem:** impossivel suportar multiplas contas sem heuristica arbitraria;
o servidor precisaria de logica de selecao que pertence ao cliente.
**Decisao:** rejeitada.

### Verificacao de ownership no controller via filtro/aspecto

**Vantagem:** reutilizavel via anotacao.
**Desvantagem:** acoplamento do controller a logica de dominio; dificulta testes
unitarios do use case; a verificacao ficaria fora da transacao do use case.
**Decisao:** rejeitada.

---

## Impacto na implementacao

O backend-engineer deve:

1. Adicionar o campo `authenticatedUserId` ao `InitiateTransferCommand`.
2. Passar `authenticatedUserId` do controller para o command.
3. Adicionar o metodo `boolean isOwner(String accountId, String userId)` ao
   `CoreBankingApi` (API publica do modulo corebanking).
4. No `InitiateTransferUseCase`, antes de chamar `coreBankingApi.debit()`,
   verificar `coreBankingApi.isOwner(command.sourceAccountId(), command.authenticatedUserId())`.
   Se retornar false, lancar `AccountAccessDeniedException` (HTTP 403).

Esta verificacao deve ocorrer dentro da transacao `@Transactional` do use case,
apos a verificacao de idempotencia e antes do `Transfer.initiate()`.
