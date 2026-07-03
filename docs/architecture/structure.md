# Estrutura de Pacotes — distributed-bank

Monólito modular (Spring Modulith). Um diretório por bounded context em
`src/main/java/com/nexusbank/`. Cada módulo é hexagonal por dentro. O backend-engineer
deve seguir esta estrutura exatamente; desvios precisam de aprovação do architect.

## Hierarquia completa

```
src/main/java/com/nexusbank/
│
├── NexusBankApplication.java          <- ponto de entrada Spring Boot
│
├── identity/                          <- módulo Identity (autenticação, usuários)
│   ├── IdentityApi.java               <- API pública do módulo (interface ou classe
│   │                                     de fachada visível aos outros módulos)
│   ├── domain/
│   │   ├── model/                     <- User, Credential, Role (sem anotações JPA)
│   │   ├── event/                     <- UserRegistered, UserLocked (records)
│   │   ├── exception/                 <- UserNotFoundException, InvalidCredentialException
│   │   └── port/
│   │       ├── in/                    <- UserRepository (interface), TokenStore (interface)
│   │       └── out/                   <- (portas de saída usadas pelo domínio)
│   ├── application/
│   │   ├── usecase/                   <- RegisterUser, AuthenticateUser, RefreshToken
│   │   └── dto/                       <- RegisterUserCommand, AuthResult (records)
│   └── adapter/
│       ├── in/
│       │   └── web/                   <- AuthController, request/response DTOs, mappers
│       └── out/
│           ├── persistence/           <- UserJpaEntity, UserJpaRepository, UserPersistenceAdapter
│           └── messaging/             <- (publishers de eventos de domínio, se necessário)
│
├── corebanking/                       <- módulo Core Banking (contas, saldo, extrato)
│   ├── CoreBankingApi.java
│   ├── domain/
│   │   ├── model/                     <- Account, Customer, Balance, Money (value object),
│   │   │                                 Currency, AccountStatus
│   │   ├── event/                     <- AccountOpened, BalanceUpdated
│   │   ├── exception/                 <- InsufficientFundsException, AccountNotFoundException
│   │   └── port/
│   │       ├── in/                    <- AccountRepository, CustomerRepository
│   │       └── out/                   <- (portas de saída: ex. CurrencyConverter)
│   ├── application/
│   │   ├── usecase/                   <- OpenAccount, GetBalance, GetStatement,
│   │   │                                 DebitAccount, CreditAccount
│   │   └── dto/                       <- OpenAccountCommand, StatementQuery, StatementResult
│   └── adapter/
│       ├── in/
│       │   ├── web/                   <- AccountController, CustomerController, DTOs
│       │   └── messaging/             <- consumers de eventos de outros módulos (se houver)
│       └── out/
│           ├── persistence/           <- AccountJpaEntity, AccountJpaRepository,
│           │                             AccountPersistenceAdapter, read model para extrato
│           └── messaging/             <- publishers de eventos (AccountOpened -> Kafka)
│
├── payments/                          <- módulo Payments (transferências, Saga, Outbox)
│   ├── PaymentsApi.java
│   ├── domain/
│   │   ├── model/                     <- Transfer, TransferStatus, PaymentType (PIX/TED/INTERNAL)
│   │   │                                 IdempotencyKey (value object)
│   │   ├── event/                     <- TransferInitiated, TransferCompleted, TransferFailed
│   │   ├── exception/                 <- DuplicateTransferException, TransferNotFoundException
│   │   └── port/
│   │       ├── in/                    <- TransferRepository, OutboxRepository
│   │       └── out/                   <- AccountDebitor, AccountCreditor
│   │                                     (portas que acessam CoreBanking via API pública)
│   ├── application/
│   │   ├── usecase/                   <- InitiateTransfer, ProcessTransferSaga,
│   │   │                                 CompensateTransfer, SchedulePayment
│   │   ├── saga/                      <- TransferSaga, TransferSagaState
│   │   └── dto/                       <- InitiateTransferCommand, TransferResult
│   └── adapter/
│       ├── in/
│       │   ├── web/                   <- TransferController, DTOs de request/response
│       │   └── messaging/             <- consumers Kafka (ex. resposta de validação Fraud)
│       └── out/
│           ├── persistence/           <- TransferJpaEntity, OutboxJpaEntity,
│           │                             TransferPersistenceAdapter, OutboxPersistenceAdapter
│           └── messaging/             <- OutboxPublisher (polling + Kafka producer)
│
├── notifications/                     <- módulo Notifications (consumo de eventos, alertas)
│   │                                     * extraído para microsserviço na Fase 4 *
│   ├── NotificationsApi.java
│   ├── domain/
│   │   ├── model/                     <- Notification, NotificationChannel, NotificationStatus
│   │   ├── event/                     <- (não publica eventos; só consome)
│   │   ├── exception/                 <- NotificationDeliveryException
│   │   └── port/
│   │       ├── in/                    <- NotificationRepository
│   │       └── out/                   <- NotificationSender (interface: email, push, SMS)
│   ├── application/
│   │   ├── usecase/                   <- SendNotification, ListNotifications, MarkAsRead
│   │   └── dto/                       <- NotificationCommand, NotificationView
│   └── adapter/
│       ├── in/
│       │   ├── web/                   <- NotificationController (lista, lê, marca como lida)
│       │   └── messaging/             <- TransferCompletedConsumer, AccountOpenedConsumer,
│       │                                 FraudSuspectedConsumer (Kafka), DLQ handler
│       └── out/
│           ├── persistence/           <- NotificationJpaEntity, NotificationPersistenceAdapter
│           └── messaging/             <- LogNotificationSender (simulado; substituto real na v2)
│
└── fraud/                             <- módulo Fraud (score, revisão, auditoria)
    ├── FraudApi.java
    ├── domain/
    │   ├── model/                     <- FraudEvaluation, RiskScore, FraudDecision
    │   │                                 (APPROVED / REVIEW / BLOCKED)
    │   ├── event/                     <- FraudSuspected, FraudCleared
    │   ├── exception/                 <- FraudEvaluationException
    │   └── port/
    │       ├── in/                    <- FraudEvaluationRepository, AuditRepository
    │       └── out/                   <- (portas para regras externas, se necessário)
    ├── application/
    │   ├── usecase/                   <- EvaluateTransfer, ReviewFraudCase, AuditDecision
    │   └── dto/                       <- EvaluateTransferCommand, FraudResult
    └── adapter/
        ├── in/
        │   ├── web/                   <- FraudController (consulta de avaliações, revisão manual)
        │   └── messaging/             <- TransferInitiatedConsumer (avalia em async)
        └── out/
            ├── persistence/           <- FraudEvaluationJpaEntity, AuditJpaEntity,
            │                             FraudPersistenceAdapter
            └── messaging/             <- FraudSuspectedPublisher (Kafka)
```

## Regras de dependencia entre camadas

```
adapter.in.*  -->  application  -->  domain
adapter.out.* -->  domain (implementa portas)
```

Nenhuma seta aponta para fora do domínio a partir do domain. Nenhum adapter conhece
outro adapter. O ArchUnit verifica isso no CI.

## Regras de visibilidade entre modulos (Spring Modulith)

Cada módulo expoe apenas o que está declarado no pacote raiz do módulo
(`com.nexusbank.<modulo>`). Tudo dentro dos subpacotes (`domain`, `application`,
`adapter`) é tratado como interno. O Spring Modulith aplica essa regra via
`ApplicationModules.verify()`.

Comunicacao permitida entre módulos:
- `payments` chama a API pública de `corebanking` para debitar/creditar contas.
- `fraud` consome o evento `TransferInitiated` publicado por `payments`.
- `notifications` consome eventos de `payments` (`TransferCompleted`, `TransferFailed`)
  e de `corebanking` (`AccountOpened`).
- `identity` não depende de nenhum outro módulo de negócio.

## Convencoes de nomenclatura

| Camada | Sufixo esperado | Exemplo |
|---|---|---|
| Caso de uso (application) | nenhum (verbo + substantivo) | `OpenAccount`, `InitiateTransfer` |
| Command/Query DTO | `Command` / `Query` / `Result` | `OpenAccountCommand`, `TransferResult` |
| Controller | `Controller` | `AccountController` |
| Request/Response DTO | `Request` / `Response` | `InitiateTransferRequest` |
| Entidade JPA | `JpaEntity` | `AccountJpaEntity` |
| Adaptador de persistência | `PersistenceAdapter` | `AccountPersistenceAdapter` |
| Spring Data Repository | `JpaRepository` | `AccountJpaRepository` |
| Evento de domínio | substantivo no passado | `AccountOpened`, `TransferCompleted` |
| Excecao de domínio | `Exception` | `InsufficientFundsException` |

Todos os identificadores (classes, métodos, variáveis, pacotes) em inglês.
Mensagens de erro, logs e comentários em português brasileiro.

## Pacote de infraestrutura transversal

Configuracoes que cortam todos os módulos ficam em `com.nexusbank.infrastructure`:

```
src/main/java/com/nexusbank/infrastructure/
├── config/          <- SecurityConfig, KafkaConfig, RedisConfig, JpaConfig
├── outbox/          <- OutboxPoller (job de polling do Outbox, transversal a Payments)
├── exception/       <- GlobalExceptionHandler (ProblemDetail RFC 9457)
└── observability/   <- métricas customizadas, configuração de tracing
```

Esse pacote não é um módulo Spring Modulith; é infraestrutura compartilhada. Módulos
de negócio não importam classes daqui, exceto via auto-configuração do Spring.

## Schemas de banco de dados

Cada módulo usa um schema PostgreSQL próprio para isolar dados:

| Módulo | Schema |
|---|---|
| identity | `identity` |
| corebanking | `corebanking` |
| payments | `payments` |
| notifications | `notifications` |
| fraud | `fraud` |

As migrations Flyway de cada módulo ficam em
`src/main/resources/db/migration/<modulo>/`. O Flyway é configurado para rodar
todos os schemas na inicialização.
