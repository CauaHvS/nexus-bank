# ADR-009 — Arquitetura do Módulo Notifications

## Status
Aceito

## Contexto

O módulo `notifications` precisa ser definido estruturalmente antes da implementação
da Fatia 4.1 (in-process + Kafka) e da Fatia 4.2 (extração para microsserviço físico).
Este ADR registra as decisões de domínio, portas, adaptadores e estrutura de pacotes.
As decisões de DLQ e de entrega do `AccountOpened` estão em ADR-007 e ADR-008.

## Domínio

### Aggregate: Notification

Campos:
- `notificationId: NotificationId` (wrapper de UUID — imutável após criação)
- `userId: String` (UUID do usuário dono da notificação; String para desacoplamento)
- `type: NotificationType` (enum)
- `title: String`
- `body: String`
- `read: boolean`
- `createdAt: Instant`

Invariantes:
- Uma `Notification` nasce sempre com `read = false`.
- `markAsRead()` é idempotente: chamar em notificação já lida não lança erro.
- `notificationId` é gerado no factory `create(...)` — nunca pelo banco.

Factory (método estático):
```java
Notification.create(String userId, NotificationType type, String title, String body)
```

Métodos de comportamento:
```java
void markAsRead()
```

Evento de domínio publicado após criação:
```java
record NotificationCreated(NotificationId notificationId, String userId, NotificationType type)
```

### Enum: NotificationType

```java
public enum NotificationType {
    TRANSFER_COMPLETED,
    TRANSFER_FAILED,
    ACCOUNT_OPENED
}
```

### Value Object: NotificationId

```java
public record NotificationId(UUID value) {
    public static NotificationId generate() { return new NotificationId(UUID.randomUUID()); }
    public static NotificationId of(UUID value) { return new NotificationId(value); }
    public static NotificationId of(String value) { return new NotificationId(UUID.fromString(value)); }
}
```

## Portas (domain/port)

### Porta de saída: NotificationRepository

```java
public interface NotificationRepository {
    Notification save(Notification notification);
    Page<Notification> findByUserId(String userId, Pageable pageable);
    long countUnread(String userId);
    void markAllAsRead(String userId);
    Optional<Notification> findByIdAndUserId(NotificationId id, String userId);
}
```

Nota: `markAllAsRead` opera no banco diretamente (UPDATE em lote) — não carrega
entidades em memória para não degradar com volume alto.

### Porta de saída: NotificationSender

```java
public interface NotificationSender {
    void send(Notification notification);
}
```

Implementação da Fatia 4.1: `LogNotificationSender` — apenas loga. A interface
está definida para substituição futura por email/push/SMS sem tocar no domínio.

### Porta de saída: DlqRepository

```java
public interface DlqRepository {
    void save(String topic, String payload, String errorMessage);
    List<DlqEntry> findPendingForRetry(int maxRetryCount);
    void incrementRetryCount(UUID id);
    void markExhausted(UUID id);
}
```

`DlqEntry` é um DTO (não aggregate):
```java
public record DlqEntry(UUID id, String topic, String payload, int retryCount) {}
```

## Casos de uso (application/usecase)

### CreateNotificationUseCase

- Recebe: `userId`, `type`, `title`, `body`
- Cria `Notification` via factory
- Persiste via `NotificationRepository.save`
- Chama `NotificationSender.send`
- Publica `NotificationCreated` via `ApplicationEventPublisher`
- Idempotente: a idempotência está na chave de processamento do Kafka listener
  (ver adaptador in/messaging), não aqui

### GetNotificationsUseCase

- Recebe: `userId`, `page`, `size`
- Retorna `NotificationListResult` (DTO de aplicação com paginação + unreadCount)
- `unreadCount` é calculado por `NotificationRepository.countUnread` em paralelo
  à query paginada

### MarkAsReadUseCase

- Recebe: `notificationId` (String), `userId`
- Busca com `findByIdAndUserId` — lança `NotificationNotFoundException` se não achar
- Chama `markAsRead()` no aggregate
- Salva

### MarkAllAsReadUseCase

- Recebe: `userId`
- Delega para `NotificationRepository.markAllAsRead`
- Sem retorno (void)

## DTOs de aplicação (application/dto)

```java
public record NotificationView(
    String id,          // UUID como String
    String userId,
    String type,        // nome do enum
    String title,
    String body,
    boolean read,
    Instant createdAt
) {}

public record NotificationListResult(
    List<NotificationView> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    long unreadCount
) {}
```

## Adaptadores

### adapter/in/messaging/KafkaNotificationListener

- `@KafkaListener` para os três tópicos (pode ser um listener com múltiplos topics
  ou três métodos separados — prefira três métodos para clareza de log e tratamento
  de erro independente).
- Deserializa o `payload` do formato Outbox (String JSON) para os campos do evento.
- Deriva `title` e `body` com base no tipo do evento e nos campos do payload.
- Chama `CreateNotificationUseCase`.
- Em caso de falha após 3 tentativas: grava na `notification_dlq` via `DlqRepository`.
- Comita o offset independentemente de sucesso ou falha (para não travar o tópico).
- O scheduler de reprocessamento (`DlqReprocessorScheduler`, fixedDelay=60s) busca
  registros pendentes na DLQ e tenta criar a notificação novamente.

Geração de texto das notificações (responsabilidade do adaptador, não do domínio):

| Tipo               | title                       | body                                                  |
|--------------------|-----------------------------|-------------------------------------------------------|
| TRANSFER_COMPLETED | "Transferência concluída"   | "Sua transferência de {amount} foi concluída."        |
| TRANSFER_FAILED    | "Transferência falhou"      | "Sua transferência falhou: {reason}."                 |
| ACCOUNT_OPENED     | "Conta aberta com sucesso"  | "Sua conta {accountNumber} foi aberta."               |

### adapter/out/persistence/NotificationPersistenceAdapter

- Implementa `NotificationRepository`
- `markAllAsRead`: usa `@Modifying @Query("UPDATE ...")` no JPA repository para
  UPDATE em lote (não carrega entidades)

### adapter/out/persistence/DlqPersistenceAdapter

- Implementa `DlqRepository`
- `findPendingForRetry`: busca registros com `retry_count < :maxRetryCount AND exhausted = false`

### adapter/out/notification/LogNotificationSender

- Implementa `NotificationSender`
- `log.info("Notificação enviada: userId={} type={} title={}", ...)`

### adapter/in/web/NotificationController

- Endpoints conforme `docs/openapi/notifications.yaml`
- Extrai `userId` do `SecurityContextHolder` (principal.getName() retorna o UUID
  do usuário — mesmo padrão dos outros controllers do projeto)
- Retorna `ResponseEntity` com os DTOs mapeados de `NotificationView`

## Estrutura de pacotes final

```
com.nexusbank.notifications/
  NotificationsApi.java                           (interface pública — vazia)
  package-info.java                               (@ApplicationModule)
  domain/
    model/
      Notification.java
      NotificationId.java
      NotificationType.java
    event/
      NotificationCreated.java
    port/out/
      NotificationRepository.java
      NotificationSender.java
      DlqRepository.java
    exception/
      NotificationNotFoundException.java
  application/
    usecase/
      CreateNotificationUseCase.java
      GetNotificationsUseCase.java
      MarkAsReadUseCase.java
      MarkAllAsReadUseCase.java
    dto/
      NotificationView.java
      NotificationListResult.java
      DlqEntry.java
  adapter/
    in/
      messaging/
        KafkaNotificationListener.java
        DlqReprocessorScheduler.java
      web/
        NotificationController.java
    out/
      persistence/
        NotificationJpaEntity.java
        NotificationJpaRepository.java
        NotificationPersistenceAdapter.java
        DlqJpaEntity.java
        DlqJpaRepository.java
        DlqPersistenceAdapter.java
      notification/
        LogNotificationSender.java
```

## Schema SQL (migration: notifications/V1__init_schema.sql)

```sql
CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TABLE notifications.notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     VARCHAR(36)  NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT         NOT NULL,
    read        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_created
    ON notifications.notifications (user_id, created_at DESC);

CREATE INDEX idx_notifications_unread
    ON notifications.notifications (user_id)
    WHERE read = FALSE;

CREATE TABLE notifications.notification_dlq (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic         VARCHAR(255) NOT NULL,
    payload       TEXT         NOT NULL,
    error_message TEXT,
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    exhausted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_retry_at TIMESTAMPTZ
);
```

## Tópicos Kafka (configuração em application.properties)

```properties
# Grupos de consumer do módulo Notifications
spring.kafka.consumer.group-id=notifications-service
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer

# Tópicos consumidos (referência; os nomes são hardcoded nos @KafkaListener)
notifications.topics.transfer-completed=payments.transfer.completed
notifications.topics.transfer-failed=payments.transfer.failed
notifications.topics.account-opened=corebanking.account.opened

# DLQ
notifications.dlq.max-retry-count=${NOTIFICATIONS_DLQ_MAX_RETRY:5}
notifications.dlq.reprocess-delay-ms=${NOTIFICATIONS_DLQ_REPROCESS_DELAY:60000}
```

## Dependências de módulo (Spring Modulith)

O módulo Notifications não depende de nenhum outro módulo via chamada direta.
Consome apenas mensagens Kafka (adaptador in/messaging). O `package-info.java`
do módulo raiz não precisa declarar `allowedDependencies`.

Se o `NotificationController` precisar de tipos de exceção compartilhados
(ex: `ProblemDetail`), esses já estão no `GlobalExceptionHandler` da infra
transversal, que não é um módulo Modulith — é infraestrutura de aplicação.

## Consequências positivas

- Domínio puro: `Notification`, `NotificationId`, `NotificationType` não importam
  Spring, JPA nem Kafka.
- Módulo isolado: nenhum outro módulo o chama diretamente.
- Portável: a estrutura funciona identicamente in-process (Fatia 4.1) e como
  microsserviço físico (Fatia 4.2) — basta mover o pacote e ajustar configurações
  de conexão.

## Consequências negativas

- O CoreBanking precisa adicionar suporte a Outbox (ADR-008): pequeno aumento de
  escopo na Fatia 4.1.
- O `DlqReprocessorScheduler` adiciona uma preocupação de idempotência que o
  backend-engineer precisa tratar (usar `notificationId` como chave de idempotência
  na criação — se a notificação já existe no banco para aquele evento, ignorar).
