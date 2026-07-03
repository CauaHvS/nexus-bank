# ADR-001 — Arquitetura Hexagonal com DDD Tático por Módulo

## Status
Aceito

## Contexto

O distributed-bank cobre cinco bounded contexts com regras de negócio distintas e
não triviais: Identity (autenticação, MFA, tokens rotativos), Core Banking (saldo,
limite, moeda), Payments (Saga, Outbox, idempotência, compensação), Notifications
(entrega assíncrona com DLQ) e Fraud (score, revisão, auditoria).

Cada contexto tem invariantes próprias que precisam ser protegidas independentemente
de qual framework, banco ou protocolo de transporte está em uso. A experiência com
sistemas financeiros mostra que misturar regras de negócio com detalhes de
infraestrutura (anotações JPA no domínio, lógica em controllers, serviços que
dependem diretamente de repositórios concretos) resulta em código frágil, difícil
de testar sem container e impossível de evoluir sem efeitos colaterais.

O projeto precisa ainda demonstrar maturidade de portfólio: testes de domínio sem
Spring, substituição de adaptadores sem tocar o núcleo, e uma linha clara entre o
que é política de negócio e o que é detalhe de implementação.

## Decisão

Adotar arquitetura hexagonal (Ports and Adapters) com DDD tático dentro de cada
módulo Spring Modulith.

Estrutura interna de cada módulo:

```
<modulo>/
  domain/          <- núcleo puro: entidades, value objects, agregados, eventos de
                      domínio, exceções de domínio, interfaces de porta (repository,
                      serviços externos). Nenhuma dependência de framework.
  application/     <- casos de uso (serviços de aplicação): orquestram o domínio,
                      definem as portas de entrada (interfaces que os adaptadores
                      de entrada implementam ou chamam). Depende só do domain.
  adapter/
    in/
      web/         <- adaptadores de entrada HTTP: controllers Spring MVC,
                      DTOs de request/response, mapeamento para/do domínio.
      messaging/   <- adaptadores de entrada de mensageria: consumers Kafka,
                      listeners de eventos Spring.
    out/
      persistence/ <- adaptadores de saída de persistência: implementações JPA dos
                      repositórios de domínio, entidades JPA, mappers.
      messaging/   <- adaptadores de saída de mensageria: publishers Kafka,
                      implementação do Outbox.
```

Regras de dependência, sem exceção:
- `domain` não importa nada externo (nenhuma anotação Spring, nenhuma classe JPA,
  nenhuma interface Kafka).
- `application` importa `domain`. Não importa adapters.
- `adapter.*` importa `application` e `domain`. Nunca o inverso.
- A violação dessas regras quebra o build via ArchUnit.

API pública do módulo (o que outros módulos podem ver) é declarada no pacote raiz
do módulo como interfaces e DTOs de eventos. Tudo dentro dos subpacotes é
`package-private` onde possível, reforçado pelo Spring Modulith.

## Consequencias Positivas

- Domínio testável sem Spring: testes unitários de regras de negócio são puros,
  rápidos e determinísticos.
- Substituição de adaptador sem tocar o domínio: trocar JPA por JDBC, ou HTTP por
  gRPC, não exige mudança nas regras de negócio.
- Fronteiras claras entre contextos: o Spring Modulith verifica em tempo de build
  (ApplicationModules.verify) que módulos não acessam internals uns dos outros.
- Facilita extração futura: o módulo Notifications, quando extraído para
  microsserviço (Fase 4), já tem uma fronteira limpa; só os adaptadores mudam.
- Testabilidade: portas são interfaces, adaptadores são substituíveis por mocks ou
  implementações in-memory nos testes de integração de domínio.

## Consequencias Negativas

- Mais camadas = mais arquivos para casos simples: um CRUD básico precisa de porta,
  caso de uso, adaptador de entrada e adaptador de saída, contra dois ou três
  arquivos numa arquitetura em camadas simples.
- Curva de aprendizado para contribuidores menos familiarizados com o padrão.
- Risco de mapper proliferation: sem disciplina, mapeamentos entre DTO, entidade JPA
  e objeto de domínio triplicam o boilerplate. Mitigação: usar record como DTO e
  manter mapeamentos simples e explícitos (sem MapStruct a menos que o volume
  justifique).

## Alternativas Consideradas

### Modelo anêmico (entities com getters/setters, lógica nos services)

Mais simples de entender à primeira vista. Amplamente adotado em projetos Java
corporativos legados. O problema é que as invariantes ficam espalhadas pelos
serviços e qualquer serviço pode modificar o estado de qualquer entidade
diretamente. Em um contexto de banco digital, onde saldo nunca pode ser negativo e
transferências precisam ser atômicas, o modelo anêmico convida a bugs de
consistência. Descartado.

### Arquitetura em camadas tradicional (controller > service > repository)

Simples, bem entendida, suficiente para CRUDs. O problema é que a camada de serviço
acaba dependendo de JPA diretamente, testes de serviço exigem banco, e regras de
negócio ficam acopladas à infraestrutura. Quando o projeto cresce (Saga, Outbox,
compensação), a falta de fronteira explícita entre domínio e infra gera débito
técnico alto. Descartado para este contexto, embora seja aceitável para projetos
mais simples.
