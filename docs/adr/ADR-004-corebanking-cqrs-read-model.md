# ADR-004 — CQRS Leve e Read Model no Módulo Core Banking

## Status
Aceito

## Contexto

O módulo Core Banking tem dois perfis de acesso radicalmente diferentes:

- **Escrita**: abertura de conta, débito, crédito. Baixa frequência por conta, mas exige
  consistência estrita e proteção de invariantes (saldo nunca negativo, conta deve estar
  ativa). O agregado `Account` encapsula essas regras.
- **Leitura**: saldo e extrato são consultados com frequência muito maior — estimativa
  conservadora de 10:1 a 50:1 em relação às escritas. Um cliente consulta o saldo ao
  abrir o app, ao receber uma notificação, antes de cada pagamento. O extrato pode ter
  centenas de entradas por conta.

Ler saldo e extrato diretamente do agregado `Account` via joins na tabela de eventos ou
na tabela de entradas introduz dois problemas:

1. **Performance sob carga**: uma query de extrato paginado que une `accounts` com
   `account_entries` e aplica filtro de data é simples para uma conta, mas o plano de
   execução se degrada quando dezenas de milhares de contas fazem isso simultaneamente.
2. **Acoplamento de leitura ao modelo de escrita**: o modelo de domínio foi otimizado
   para proteger invariantes, não para projeções de leitura. Forçar esse modelo a servir
   leitura significa ou expor o estado interno do agregado ou criar queries que ignoram
   as fronteiras do domínio.

O projeto não tem, neste momento, volume que exija CQRS completo com banco separado
(projeção em banco de leitura dedicado). Isso seria over-engineering para um portfólio
em fase v1. O que existe é um padrão de acesso assimétrico que justifica uma separação
leve dentro do mesmo banco.

## Decisão

Adotar **CQRS leve** dentro do módulo Core Banking:

### Lado de escrita (command side)
- O agregado `Account` é a única fonte de verdade para as invariantes de negócio.
- Toda operação de escrita passa pelo agregado: `account.debit()`, `account.credit()`,
  `Account.open()`.
- O agregado emite eventos de domínio (`AccountOpened`, `BalanceUpdated`) que são
  processados por um handler de aplicação.

### Lado de leitura (query side)
- O handler de `BalanceUpdated` persiste uma entrada em `account_entries`
  (tabela `AccountEntry`) com: tipo (DEBIT/CREDIT), valor, descrição, timestamp e
  saldo após a operação (`balanceAfter`). Essa tabela é o read model do extrato.
- O saldo atual é armazenado em **cache Redis** com TTL de 30 segundos. Invalidado
  imediatamente ao receber evento `BalanceUpdated`.
- O endpoint `GET /accounts/{id}/balance` serve do Redis (cache hit) ou, em miss,
  lê diretamente da coluna `balance` da tabela `accounts` e repopula o cache.
- O endpoint `GET /accounts/{id}/statement` lê de `account_entries` com query
  paginada e filtro de data — nunca toca o agregado.

### Projeção
O `AccountEntry` não é um evento de domínio; é uma projeção persistida. O handler
que cria entradas no extrato é um listener de aplicação, dentro do mesmo módulo,
na mesma transação que a escrita (sem eventual consistency entre escrita e extrato
nesta versão).

Isso é deliberadamente mais simples do que CQRS com consistência eventual. A
consistência eventual entre agregado e read model pode ser introduzida na v2 se o
volume justificar.

## Consequencias Positivas

- Queries de extrato não tocam o agregado nem sofrem com lock de escrita na tabela
  `accounts`.
- Cache Redis elimina a maioria das leituras de saldo do banco em cenários de alta
  leitura.
- O modelo de domínio permanece limpo: `Account` não precisa de métodos de leitura
  paginada nem de DTOs de extrato.
- A estrutura já está preparada para, em v2, tornar a projeção assíncrona (listener
  Kafka em vez de listener de aplicação síncrono) sem alterar o domínio.
- Testes de domínio testam apenas as invariantes; testes de leitura testam apenas a
  projeção.

## Consequencias Negativas

- Mais artefatos: tabela `account_entries`, handler de projeção, lógica de cache,
  invalidação. Um CRUD simples não precisaria disso.
- O campo `balanceAfter` em `account_entries` é desnormalizaçao intencional. Se uma
  migração reprocessar eventos históricos incorretamente, o extrato pode ficar
  inconsistente com o saldo real. Mitigação: script de reconciliação periódica.
- O cache Redis introduz uma janela de até 30 segundos de saldo potencialmente
  desatualizado para leituras consecutivas muito rápidas. Janela aceitável para
  o cenário de uso (consulta de saldo pelo cliente). Não afeta a consistência
  da escrita, que sempre usa o valor no banco.

## Alternativas Consideradas

### CQRS completo com banco separado (ex: banco de leitura PostgreSQL ou Elasticsearch)

Custo operacional alto: dois bancos, sincronização por Kafka, consistência eventual
real entre escrita e leitura, infra mais complexa no Docker Compose. O volume de
dados e a carga do portfólio não justificam esse custo neste momento. Pode ser
considerado em v2 se o módulo for extraído para microsserviço (conforme ADR-002).
**Descartado para v1.**

### Sem CQRS — leitura direta do agregado Account

Simples de implementar. O problema aparece quando o extrato tem centenas de entradas:
a query precisa agregar eventos ou entradas ligadas ao `Account`, com potencial de
slow query sob carga. Além disso, expõe o estado interno do agregado para leitura,
criando pressão para adicionar métodos de conveniência que não pertencem ao domínio.
**Descartado.**

### Read model projetado assincronamente via Kafka

Consistência eventual: a entrada no extrato aparece alguns milissegundos depois da
operação. Justificável em sistemas de alto volume onde a escrita não pode esperar
a projeção. Para v1, a latência adicional de um listener síncrono na mesma transação
é insignificante e elimina a complexidade de lidar com reordenação de mensagens e
reprocessamento. Esse padrão está documentado como evolução natural para v2.
**Descartado para v1, reservado para v2.**
