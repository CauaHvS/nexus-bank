# ADR-010 — Extração do Módulo Notifications para Microsserviço Físico

## Status
Aceito

## Contexto

O projeto nasceu como monólito modular (ADR-002) com a decisão explícita de que a
extração seletiva de um módulo, ao final do roadmap, provaria a capacidade de
decompor sem o custo operacional de múltiplos serviços desde o início.

O módulo Notifications é o candidato ideal para essa extração pelos motivos abaixo:

1. **Sem dependências de entrada diretas.** Nenhum outro módulo chama o
   `NotificationsApi` diretamente. A API pública do módulo (`NotificationsApi.java`)
   é uma interface vazia — está lá apenas para que o `ApplicationModules.verify()`
   reconheça o módulo, não para expor contratos consumidos por outros módulos.

2. **Canal de entrada exclusivamente Kafka.** Desde a Fatia 4.1, todos os eventos
   consumidos por Notifications chegam via Kafka (ADR-008). O `AccountOpened` do
   CoreBanking é publicado via Outbox em `corebanking.account.opened` em vez de
   `ApplicationEventPublisher`. Não há nenhum `@ApplicationModuleListener` nem
   chamada in-process que precise ser removida.

3. **Canal de saída: banco de dados (schema `notifications`).** O módulo só grava
   e lê seu próprio schema, sem acesso a tabelas de outros módulos.

4. **Já hexagonal.** O `KafkaNotificationListener`, o `NotificationController` e os
   adaptadores de persistência estão isolados do domínio por portas. Mover o código
   para outro projeto não exige refatoração interna.

5. **A notificação não bloqueia nenhuma transação de negócio.** O monólito core
   publica no Kafka e segue em frente. A ausência do serviço de notifications não
   afeta a consistência de Payments ou CoreBanking.

A Fatia 4.2 é, portanto, uma extração de baixo risco e alto valor demonstrativo.

### O que muda na arquitetura

Antes da extração:

```
[Monólito nexus-bank]
  modules: identity, corebanking, payments, notifications, fraud
  deployable: 1 JAR
  banco: 1 PostgreSQL, schemas separados
```

Após a extração:

```
[Monólito nexus-bank-core]
  modules: identity, corebanking, payments, fraud
  deployable: 1 JAR, porta 8080

[Microsserviço nexus-bank-notifications]
  deployable: 1 JAR separado, porta 8081
  banco: mesmo PostgreSQL, schema notifications (somente)
  entrada: Kafka (3 tópicos)
  saída: HTTP REST /notifications/* (autenticado com JWT)
```

Comunicação entre core e notifications: **exclusivamente Kafka**. Nenhuma chamada
HTTP entre os dois serviços. O frontend chama os dois endpoints diretamente por
porta diferente (ou, em produção, por rotas de API gateway separadas).

## Decisão

Extrair o módulo Notifications para um serviço Spring Boot 3.5.3 independente,
localizado em `notifications-service/` na raiz do repositório. O core monólito remove
o pacote `com.nexusbank.notifications` e seu bean Flyway. O serviço extraído herda
a estrutura hexagonal atual sem alterações estruturais internas.

### Regras da extração

1. O serviço extraído **não importa Spring Modulith**. É um Spring Boot puro.
2. O pacote `com.nexusbank.notifications` é copiado integralmente para o novo projeto,
   exceto `NotificationsApi.java` e `package-info.java` (artefatos do Modulith).
3. O `GlobalExceptionHandler` do monólito remove o handler de `NotificationNotFoundException`.
   O serviço extraído tem seu próprio `GlobalExceptionHandler`.
4. O `SecurityConfig` do serviço extraído valida o mesmo JWT emitido pelo módulo
   Identity do monólito. O segredo JWT (`JWT_SECRET`) é compartilhado via variável de
   ambiente — sem serviço de discovery de segredos neste escopo.
5. O `FlywayConfig` do monólito remove o bean `notificationsFlyway`. O serviço extraído
   tem seu próprio `FlywayConfig` gerenciando apenas o schema `notifications`.
6. As migrations `V1`, `V2` e `V3` do schema `notifications` são copiadas para
   `notifications-service/src/main/resources/db/migration/notifications/`. Como o
   schema já existe no banco de quem fez upgrade do monólito, o Flyway do serviço
   extraído roda com `baselineOnMigrate=true` e não recria o que já existe.

### Versionamento do contrato de evento

Os tópicos Kafka (`payments.transfer.completed`, `payments.transfer.failed`,
`corebanking.account.opened`) não têm versão explícita no nome agora. A estratégia
adotada é:

- **Sem versionamento por enquanto.** Os payloads são estáveis e o consumidor
  (notifications) já usa deserialização defensiva (campos ausentes viram valores
  padrão via Jackson).
- **Quando houver breaking change** (ex: campo renomeado, tipo alterado): adicionar
  sufixo `.v2` ao nome do tópico e manter o produtor publicando nos dois tópicos
  por um período de transição. Registrar a mudança em novo ADR.
- **Campos adicionados** (backward compatible) não exigem versionamento.

## Consequências Positivas

- Prova concreta de que o isolamento modular funciona: a extração não exigiu
  refatoração do domínio nem dos adaptadores.
- O serviço de notifications pode escalar independentemente (ex: mais réplicas se o
  volume de eventos crescer).
- Deploy independente: uma atualização no serviço de notifications não exige rebuild
  do monólito core.
- O portfólio demonstra a jornada completa: monólito modular -> extração seletiva
  -> microsserviço real.

## Consequências Negativas

- **Latência adicional nas notificações.** O caminho evento -> Kafka -> serviço ->
  banco envolve dois processos. Para notificações in-app isso é aceitável (já era
  assíncrono antes).
- **Debugging mais complexo.** Uma falha em notificações exige verificar logs em dois
  processos e o estado do Kafka. Mitigado por correlation IDs nos logs.
- **Novo processo para operar.** O `docker-compose` sobe dois serviços de aplicação.
  O `healthcheck` do core não depende do notifications; o frontend precisa tratar
  a indisponibilidade do serviço de notifications sem travar a UI.
- **JWT_SECRET compartilhado.** Ambos os serviços precisam da mesma chave para
  validar tokens. Em produção, isso seria gerenciado por um secrets manager; aqui
  é uma variável de ambiente em ambos os contêineres.

## Alternativas Consideradas

### 1. Manter in-process indefinidamente

Mais simples. O módulo permanece no monólito como está. Elimina todo o overhead
operacional desta extração.

Descartado porque o objetivo do portfólio inclui demonstrar a capacidade de
decomposição. Manter in-process não prova nada sobre microsserviços.

### 2. Extrair com API Gateway na frente

Adicionar um Nginx ou Spring Cloud Gateway roteando `/notifications/*` para o serviço
extraído e o restante para o core. O frontend só conheceria uma porta.

Descartado. Adiciona complexidade operacional sem benefício real para um portfólio
onde o frontend pode chamar duas portas diretamente. Um API gateway seria justificado
se houvesse múltiplos consumidores externos ou necessidade de SSL termination — não
é o caso aqui.

### 3. Extrair com banco de dados separado (PostgreSQL próprio)

O serviço de notifications teria seu próprio servidor PostgreSQL. Isolamento total
de dados.

Descartado neste escopo. O schema `notifications` já é isolado no PostgreSQL
compartilhado. Adicionar um segundo servidor PostgreSQL aumenta o custo de infra
local sem agregar valor demonstrável. O isolamento lógico (schema separado,
Flyway separado) é suficiente para o portfólio.
