# ADR-002 — Monólito Modular em vez de Microsserviços

## Status
Aceito

## Contexto

O distributed-bank é um projeto de portfólio desenvolvido por uma pessoa, sem equipe
distribuída, sem requisito de escala independente por serviço e sem SLA de deploy
separado por bounded context. O objetivo é demonstrar senioridade técnica simulando
um ambiente de produção real.

A primeira tentação em projetos de portfólio bancário é partir direto para sete
microsserviços separados. Essa decisão traz um custo alto sem benefício real no
contexto:

- Distributed monolith: serviços que se chamam de forma síncrona, compartilham banco
  ou têm ciclos de deploy acoplados reproduzem todos os problemas de um monólito com
  toda a complexidade operacional de microsserviços.
- Overhead de operação: cada serviço exige seu próprio processo de build, imagem
  Docker, configuração de service discovery, health check, observabilidade e gestão
  de segredos. Solo, isso consome tempo de engenharia que deveria ir para regras de
  negócio.
- Transações distribuídas prematuras: em microsserviços, cada chamada de negócio
  que precisa de consistência entre serviços exige Saga ou 2PC desde o início. No
  monólito modular, a Saga é implementada onde ela faz sentido (Payments) e o restante
  usa transações locais.

A pergunta correta não é "microsserviços ou não", mas "onde a fronteira de deploy
separado traz benefício real e quando". A resposta neste projeto é: apenas quando um
módulo tem requisitos de escala, tecnologia ou equipe distintos dos demais.

Ver também ADR-001 para a estrutura interna de cada módulo.

## Decisão

Iniciar como monólito modular usando Spring Modulith. Cada bounded context é um
módulo Spring Modulith com fronteira explícita e verificada em tempo de build. A
comunicação entre módulos ocorre exclusivamente por eventos (Spring Application
Events internamente; Kafka onde a persistência do evento ou o consumo assíncrono
justificam).

No fim do roadmap (Fase 4), o módulo Notifications é extraído para um serviço
Spring Boot independente, comunicando-se exclusivamente via Kafka. Essa extração
demonstra a capacidade de decompor o monólito modular de forma controlada.

Nenhum outro módulo é extraído na v1. Investments e Cards ficam fora de escopo.

Módulos do monólito core:
- `identity` — autenticação, autorização, usuários
- `corebanking` — contas, saldo, movimentações, extrato
- `payments` — transferências, PIX, TED, Saga, Outbox
- `notifications` — consumo de eventos, envio de alertas (extraído na Fase 4)
- `fraud` — avaliação de risco, score, revisão, auditoria

Regras de comunicação entre módulos:
1. Módulo A não chama diretamente um serviço interno de módulo B.
2. A API pública de cada módulo é declarada no pacote raiz do módulo (interfaces,
   DTOs de evento). Tudo nos subpacotes é privado ao módulo.
3. ApplicationModules.verify() roda no CI e quebra o build se alguma fronteira for
   violada.

## Consequencias Positivas

- Um único deployable: build simples, debug local sem orquestrador, transações
  locais onde cabem.
- Fronteiras de módulo verificadas em build: a disciplina de não cruzar fronteiras é
  reforçada por tooling, não só por convenção.
- Padrões distribuídos sem overhead operacional prematuro: Saga, Outbox, idempotência
  e consistência eventual são implementados onde fazem sentido (Payments), não em
  toda chamada entre módulos.
- Extração controlada: quando Notifications é extraído, o contrato já existe (eventos
  Kafka), os adaptadores de mensageria já estão separados do domínio (ADR-001), e o
  risco da extração é baixo.
- Demonstra julgamento arquitetural: saber quando não dividir é tão relevante quanto
  saber como dividir.

## Consequencias Negativas

- Escala horizontal única: o deployable inteiro escala junto. Se um módulo tiver
  pico de carga muito diferente dos outros, não é possível escalar só ele sem
  extração. Aceitável para portfólio; em produção real seria um trigger de extração.
- Risco de acoplamento acidental: sem disciplina (e sem o verify do Modulith), um
  desenvolvedor pode chamar um serviço de outro módulo diretamente. O verify no CI
  mitiga isso, mas não elimina o risco durante o desenvolvimento.
- Deploy all-or-nothing: uma falha de inicialização em qualquer módulo derruba o
  serviço inteiro. Microsserviços permitiriam degradação parcial. Mitigado por testes
  de integração robustos antes do deploy.
- Banco de dados compartilhado: todos os módulos usam o mesmo PostgreSQL, com schemas
  separados por bounded context. Isso simplifica operação mas cria acoplamento
  operacional de infra. Em extração futura, o schema do módulo extraído vira banco
  próprio.

## Alternativas Consideradas

### Microsserviços desde o início (7 serviços separados)

Cada bounded context nasce como serviço independente com banco próprio, CI/CD
próprio e comunicação exclusivamente via Kafka ou REST. Essa abordagem exige
service discovery, API gateway, tracing distribuído desde o dia 1, gestão de
segredos por serviço, e cada transação de negócio vira uma Saga distribuída.

Solo, isso produz um distributed monolith mal feito: os serviços inevitavelmente
ficam acoplados por chamadas síncronas ou por um banco compartilhado descoberto
tarde, e o tempo de operação consome o tempo de negócio.

A senioridade não está em ter sete containers rodando; está em saber justificar
quando extrair e demonstrar a extração de forma controlada. Descartado para a v1.

### Monólito sem fronteiras (pacotes convencionais, sem Modulith)

Um único projeto sem verificação de fronteiras entre contextos. Mais simples de
iniciar, mas sem nada impedindo que Identity acesse repositórios de CoreBanking
diretamente. A dívida técnica se acumula até que a extração se torna cirurgia.

O Spring Modulith adiciona verificação de fronteiras com custo praticamente zero de
configuração. Não há razão para abrir mão disso. Descartado.
