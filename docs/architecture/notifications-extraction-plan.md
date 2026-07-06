# Plano Tecnico — Fatia 4.2: Extração do Notifications para Microsserviço

Decisão arquitetural registrada em ADR-010.

---

## 1. Estrutura do novo projeto

```
notifications-service/
  pom.xml
  Dockerfile
  src/
    main/
      java/com/nexusbank/notifications/
        NotificationsServiceApplication.java      (nova, substitui a entrada do módulo)
        domain/
          model/
            Notification.java                     (copiado, sem alteração)
            NotificationId.java                   (copiado, sem alteração)
            NotificationType.java                 (copiado, sem alteração)
          event/
            NotificationCreated.java              (copiado, sem alteração)
          port/out/
            NotificationRepository.java           (copiado, sem alteração)
            NotificationSender.java               (copiado, sem alteração)
            DlqRepository.java                    (copiado, sem alteração)
          exception/
            NotificationNotFoundException.java    (copiado, sem alteração)
        application/
          usecase/
            CreateNotificationUseCase.java        (copiado, sem alteração)
            GetNotificationsUseCase.java          (copiado, sem alteração)
            MarkAsReadUseCase.java                (copiado, sem alteração)
            MarkAllAsReadUseCase.java             (copiado, sem alteração)
          dto/
            NotificationView.java                 (copiado, sem alteração)
            NotificationListResult.java           (copiado, sem alteração)
            DlqEntry.java                         (copiado, sem alteração)
        adapter/
          in/
            messaging/
              KafkaNotificationListener.java      (copiado, sem alteração)
              DlqReprocessorScheduler.java        (copiado, sem alteração)
            web/
              NotificationController.java         (copiado, sem alteração)
          out/
            persistence/
              NotificationJpaEntity.java          (copiado, sem alteração)
              NotificationJpaRepository.java      (copiado, sem alteração)
              NotificationPersistenceAdapter.java (copiado, sem alteração)
              DlqJpaEntity.java                   (copiado, sem alteração)
              DlqJpaRepository.java               (copiado, sem alteração)
              DlqPersistenceAdapter.java          (copiado, sem alteração)
            notification/
              LogNotificationSender.java          (copiado, sem alteração)
        infrastructure/
          config/
            FlywayConfig.java                     (nova — somente schema notifications)
            SecurityConfig.java                   (nova — valida JWT, sem Redis)
            GlobalExceptionHandler.java           (nova — handlers locais ao serviço)
      resources/
        application.properties
        db/migration/notifications/
          V1__init_schema.sql                     (copiado de src/main/resources/...)
          V2__create_notifications_table.sql      (copiado)
          V3__create_notification_dlq.sql         (copiado)
```

Arquivos **NAO copiados** do módulo original:
- `NotificationsApi.java` — artefato Spring Modulith, sem sentido fora do monólito
- `package-info.java` — idem

---

## 2. pom.xml do notifications-service

Dependencias necessarias (sem Spring Modulith):

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.3</version>
</parent>

<!-- Web, JPA, Security, Kafka, Postgres, Flyway, Validation -->
<!-- Mesmas versoes do monolito, sem spring-modulith-* -->
<!-- Jackson ja vem transitivo pelo starter-web -->
```

O `groupId` permanece `com.nexusbank` e o `artifactId` e `nexus-bank-notifications`.

---

## 3. application.properties do notifications-service

```properties
spring.application.name=nexus-bank-notifications
server.port=8081

# Banco — mesmo PostgreSQL, acesso apenas ao schema notifications
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/nexusbank}
spring.datasource.username=${DB_USERNAME:nexus}
spring.datasource.password=${DB_PASSWORD:nexus_secret}
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false

# Kafka
spring.kafka.bootstrap-servers=${KAFKA_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=notifications-service
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer

# JWT (validacao somente — o servico nao emite tokens)
jwt.secret=${JWT_SECRET}
jwt.access-ttl=${JWT_ACCESS_TTL:900}

# DLQ
notifications.dlq.max-retry-count=${NOTIFICATIONS_DLQ_MAX_RETRY:5}
notifications.dlq.reprocess-delay-ms=${NOTIFICATIONS_DLQ_REPROCESS_DELAY:60000}
```

Nota: o servico de notifications **nao usa Redis**. O cache de extrato (CQRS) e o
token store de refresh ficam no monolito core.

---

## 4. SecurityConfig do notifications-service

O `NotificationController` exige que o usuario esteja autenticado (JWT valido).
O servico valida o token com a mesma chave simetrica (`JWT_SECRET`) usada pelo
modulo Identity do monolito core para emitir tokens. Nao ha comunicacao HTTP entre
os dois servicos para validar o token — a validacao e local.

Configuracao esperada:
- Stateless (sem sessao HTTP)
- `JwtAuthenticationFilter` equivalente ao do monolito, adaptado para o novo projeto
- Rotas publicas: nenhuma (todas as rotas /notifications/* exigem autenticacao)

O backend-engineer pode copiar o `JwtAuthenticationFilter` e o `JwtService` do
modulo Identity ou criar uma versao simplificada que apenas valida (sem emitir).
A segunda opcao e preferivel para nao criar dependencia de codigo entre os dois
projetos.

---

## 5. FlywayConfig do notifications-service

```java
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway notificationsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("notifications")
                .locations("classpath:db/migration/notifications")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)  // schema ja existe no banco legado
                .load();
    }
}
```

O `baselineOnMigrate=true` garante que o Flyway nao tente recriar o schema quando
o banco ja tem as tabelas de quem vinha usando o monolito.

---

## 6. Dockerfile do notifications-service

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/nexus-bank-notifications-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Mesmo padrao do Dockerfile do monolito. Build com `./mvnw clean package -DskipTests`
dentro do diretorio `notifications-service/`.

---

## 7. Alteracoes no monolito core

### 7.1 Remover o pacote notifications

Deletar o diretorio inteiro:

```
src/main/java/com/nexusbank/notifications/
```

### 7.2 Remover o bean Flyway de notifications

Em `FlywayConfig.java` do monolito: remover o metodo `notificationsFlyway`.

### 7.3 Remover as migrations de notifications do monolito

Deletar:

```
src/main/resources/db/migration/notifications/
```

### 7.4 Remover handler de NotificationNotFoundException

Em `GlobalExceptionHandler.java` do monolito: remover o `@ExceptionHandler` para
`NotificationNotFoundException` (a classe nao existira mais no classpath do core).

### 7.5 Verificar ApplicationModules.verify()

Apos a remocao, o teste de arquitetura do Spring Modulith deve passar sem erros.
O modulo `notifications` deixa de existir — o verify simplesmente nao o encontrara.
Se houver alguma referencia residual (import, evento), o verify apontara.

### 7.6 Frontend: atualizar a URL base das chamadas de notifications

O frontend chama `/notifications/*` hoje via o mesmo baseURL do core (porta 8080).
Apos a extracao, essas chamadas devem ir para a porta 8081.

Opcao A: variavel de ambiente `VITE_NOTIFICATIONS_URL=http://localhost:8081` com
cliente Axios separado para o servico de notifications.

Opcao B: configurar o Nginx (se adicionado futuramente) para rotear
`/notifications/*` para a porta 8081 de forma transparente.

Para este portfólio, a Opcao A e suficiente e mais simples. O backend-engineer
define a URL; o frontend-engineer cria o segundo cliente Axios.

---

## 8. docker-compose atualizado

Adicionar os dois servicos de aplicacao ao `docker-compose.yml`:

```yaml
  nexus-bank-core:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: nexus-bank-core
    profiles: [app]
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/nexusbank
      DB_USERNAME: ${POSTGRES_USER:-nexus}
      DB_PASSWORD: ${POSTGRES_PASSWORD:-nexus_secret}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-redis}
      KAFKA_SERVERS: kafka:9092
      JWT_SECRET: ${JWT_SECRET}
      JWT_ACCESS_TTL: ${JWT_ACCESS_TTL:-900}
      JWT_REFRESH_TTL: ${JWT_REFRESH_TTL:-604800}
    restart: unless-stopped

  nexus-bank-notifications:
    build:
      context: notifications-service
      dockerfile: Dockerfile
    container_name: nexus-bank-notifications
    profiles: [app]
    ports:
      - "8081:8081"
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/nexusbank
      DB_USERNAME: ${POSTGRES_USER:-nexus}
      DB_PASSWORD: ${POSTGRES_PASSWORD:-nexus_secret}
      KAFKA_SERVERS: kafka:9092
      JWT_SECRET: ${JWT_SECRET}
      JWT_ACCESS_TTL: ${JWT_ACCESS_TTL:-900}
    restart: unless-stopped
```

Pontos importantes:
- `nexus-bank-notifications` **nao depende** de `nexus-bank-core`. Os dois sobem
  independentemente.
- `nexus-bank-notifications` **nao tem** `REDIS_HOST` — nao usa Redis.
- O profile `app` isola os servicos de aplicacao do profile `infra` (postgres,
  kafka, redis). Infra sobe com `docker compose --profile infra up -d`; aplicacao
  com `docker compose --profile app up -d --build`.
- O `KAFKA_ADVERTISED_LISTENERS` do Kafka precisa ser ajustado para `kafka:9092`
  (nome do servico Docker) quando os clientes estao dentro da rede Docker. O
  listener atual esta como `localhost:9092`, adequado para dev local. Em compose,
  ajustar para expor tanto `PLAINTEXT://kafka:9092` (interno) quanto
  `PLAINTEXT_HOST://localhost:9092` (externo). O backend-engineer trata isso.

---

## 9. Prova de desacoplamento (roteiro de validacao)

Este roteiro demonstra que a notificacao nao bloqueia a transacao:

1. Subir somente a infra e o monolito core (sem o servico de notifications):
   ```
   docker compose --profile infra up -d
   docker compose --profile app up -d nexus-bank-core
   ```

2. Fazer login e executar uma transferencia via API ou frontend.

3. Verificar que a transferencia conclui com status `COMPLETED`. O core nao sabe
   nem se importa que o servico de notifications esta fora.

4. Verificar no Kafka que a mensagem esta no topico `payments.transfer.completed`
   com offset pendente (grupo `notifications-service` nao consumiu):
   ```
   kafka-consumer-groups --bootstrap-server localhost:9092 \
     --group notifications-service --describe
   ```

5. Subir o servico de notifications:
   ```
   docker compose --profile app up -d nexus-bank-notifications
   ```

6. Verificar nos logs do servico que ele processou as mensagens pendentes
   (`auto-offset-reset=earliest` garante que le desde o inicio do grupo):
   ```
   docker logs nexus-bank-notifications --follow
   ```

7. Chamar `GET http://localhost:8081/notifications` com o JWT do usuario que fez
   a transferencia. A notificacao deve aparecer.

---

## 10. Ordem de implementacao recomendada para o backend-engineer

1. Criar `notifications-service/pom.xml` com as dependencias corretas (sem Modulith).
2. Criar `NotificationsServiceApplication.java`.
3. Copiar o pacote `com.nexusbank.notifications` (exceto `NotificationsApi` e
   `package-info`).
4. Criar `FlywayConfig`, `SecurityConfig` e `GlobalExceptionHandler` no novo servico.
5. Criar `application.properties` com porta 8081.
6. Criar `Dockerfile`.
7. Compilar e rodar localmente: `./mvnw clean package && java -jar target/*.jar`.
8. Remover o modulo notifications do monolito (secao 7 deste plano).
9. Rodar `./mvnw clean verify` no monolito — deve passar incluindo o
   `ApplicationModules.verify()`.
10. Atualizar o `docker-compose.yml` (secao 8).
11. Executar o roteiro de validacao da secao 9.
