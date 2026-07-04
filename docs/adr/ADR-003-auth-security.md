# ADR-003 — Estratégia de Autenticação e Segurança do Módulo Identity

## Status
Aceito

## Contexto

O distributed-bank é um banco digital. Qualquer acesso a recursos financeiros (saldo,
transferências, extrato) requer autenticação confiável. Os requisitos que guiam esta
decisão são:

1. **Stateless por padrão**: o servidor não deve manter sessão em memória. Com Spring
   Modulith e possível extração futura de módulos (ADR-002), sessões server-side
   criariam acoplamento de estado entre instâncias.
2. **Revogação eficiente**: mesmo em cenário stateless, deve ser possível invalidar a
   sessão de um usuário (logout, suspeita de comprometimento de conta) sem esperar
   expiração natural.
3. **Senhas protegidas**: senhas nunca armazenadas em texto plano; hash resistente a
   brute-force.
4. **Evolução para MFA**: a estratégia deve permitir adicionar TOTP (autenticação de
   dois fatores) na Fase 1.3 sem redesenho da camada de autenticação.

## Decisão

Adotar JWT stateless com refresh token rotativo e BCrypt para hash de senha.

### Detalhes de implementação

**Access token (JWT)**
- Algoritmo: HS256 (HMAC-SHA256) com segredo configurado via variável de ambiente.
- TTL: 15 minutos.
- Claims mínimas: `sub` (userId), `email`, `role`, `iat`, `exp`.
- Não armazenado no servidor; validado somente pela assinatura e expiração.
- Consequência aceita: não pode ser revogado antes do vencimento. O TTL curto de 15
  minutos limita a janela de abuso.

**Refresh token (JWT rotativo)**
- TTL: 7 dias.
- Armazenado no Redis como hash SHA-256 do token (nunca o token em texto plano),
  associado ao userId.
- A cada renovação (`POST /auth/refresh`), o token antigo é invalidado e um novo par
  é emitido atomicamente via Lua script no Redis (operação compare-and-swap).
- Uso de um refresh token já consumido indica possível replay attack: todos os tokens
  do usuário são revogados imediatamente.
- Logout (`POST /auth/logout`) revoga o refresh token do Redis.

**Hash de senha**
- BCrypt com fator de custo 12.
- A biblioteca `spring-security-crypto` é usada exclusivamente como utilitário de
  hash; a lógica de autenticação permanece no domínio por meio da porta `TokenStore`
  e do caso de uso `AuthenticateUserUseCase`.

**MFA TOTP (Fase 1.3)**
- A decisão atual não acopla MFA ao fluxo de login. Na Fase 1.3, um segundo passo
  será adicionado: após validar senha, o caso de uso verifica se MFA está habilitado
  e, se sim, exige o código TOTP antes de emitir tokens.
- O domínio já reserva espaço para este estado por meio de `UserStatus` e métodos de
  ativação/bloqueio no agregado `User`.

## Consequencias Positivas

- Sem estado de sessão no servidor: qualquer instância do monólito valida o access
  token independentemente, sem necessidade de sessão distribuída.
- Escalável: Redis é o único ponto de estado para refresh tokens; é naturalmente
  clusterizável.
- Revogação possível: logout e bloqueio de conta funcionam de imediato via Redis.
- Refresh rotativo detecta replay: se um token comprometido for reutilizado, o usuário
  legítimo percebe na próxima renovação (recebe 401 e é forçado a re-autenticar).
- MFA não exige redesenho: o fluxo é extensível sem quebrar contratos existentes.

## Consequencias Negativas

- Access token não revogável imediatamente: bloqueio de conta só impede novas
  autenticações; sessões ativas continuam válidas por até 15 minutos. Mitigação: TTL
  curto e, se necessário futuro, blocklist de JTI (JWT ID) no Redis.
- Dependência de Redis no caminho de autenticação: se o Redis estiver indisponível,
  refresh e logout falham. Mitigação: Redis com replicação e sentinel/cluster em
  produção; aceitável para portfólio.
- Complexidade do refresh rotativo: a atomicidade do compare-and-swap via Lua script
  no Redis adiciona lógica não trivial no adaptador de saída `RedisTokenStore`.
- Segredo JWT em variável de ambiente: rotação do segredo invalida todos os tokens
  ativos. Em produção real, usar assimetria (RS256) e JWKS endpoint permitiria rotação
  sem invalidação em massa. Deixado como melhoria futura (não justifica complexidade na v1).

## Alternativas Consideradas

### Sessão server-side (Spring Session + Redis)

Abordagem clássica: servidor cria sessão, cliente recebe cookie com session ID,
servidor valida o ID a cada requisição. Simples de implementar com Spring Session.

O problema é o acoplamento ao Redis em toda requisição (não só no refresh), a
dependência de cookies (incomum para APIs REST consumidas por SPA), e a dificuldade
de validação stateless em cenários de extração de módulos (ADR-002). Descartado.

### OAuth2 externo com Keycloak

Delegar autenticação a um servidor OAuth2 (Keycloak, Auth0, Cognito) retira a
responsabilidade de segurança do monólito e oferece SSO, MFA gerenciado, rotação de
chaves e auditoria prontos.

Para um portfólio técnico, terceirizar autenticação oculta exatamente o que se quer
demonstrar: domínio de Identity bem modelado, JWT, refresh rotativo, BCrypt. Além
disso, Keycloak adiciona um container pesado ao ambiente de desenvolvimento. Descartado
para v1; válido em produção real com múltiplos sistemas clientes.

### Opaque tokens (UUID aleatório, validação sempre via banco)

Tokens opacos eliminam o problema do access token não revogável: cada requisição
valida o token no banco. Simples e totalmente revogável.

O custo é uma query ao banco em cada requisição autenticada, o que cria um gargalo
de I/O. Sem o benefício do JWT stateless, perde-se a escalabilidade horizontal.
Descartado.
