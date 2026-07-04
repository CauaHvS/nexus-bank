/**
 * Porta de saída: contrato que o domínio define para armazenar e validar refresh tokens.
 *
 * Posicionada em port/out porque é o domínio/aplicação que define o contrato e o
 * adaptador Redis que implementa. O domínio não conhece Redis.
 *
 * Semântica de segurança:
 * - Tokens são armazenados como hash SHA-256 (nunca o valor em texto plano).
 * - validateAndRotate é atômica: invalida o token antigo e armazena o novo em uma
 *   única operação (Lua script no Redis), evitando race condition em uso concorrente.
 * - Se validateAndRotate retornar empty, o token já foi usado ou não existe:
 *   o caso de uso deve revogar todos os tokens do usuário (possível replay attack).
 *
 * O adaptador concreto vive em:
 *   adapter/out/persistence/RedisTokenStore (implementa esta interface)
 */
package com.nexusbank.identity.domain.port.out;

import com.nexusbank.identity.domain.model.UserId;

import java.time.Duration;
import java.util.Optional;

public interface TokenStore {

    /**
     * Armazena o hash de um novo refresh token associado ao usuário.
     *
     * @param userId    identificador do usuário
     * @param tokenHash hash SHA-256 do refresh token
     * @param ttl       tempo de expiração
     */
    void storeRefreshToken(UserId userId, String tokenHash, Duration ttl);

    /**
     * Valida o token existente e, atomicamente, substitui pelo novo hash.
     * Retorna o UserId associado se a operação for bem-sucedida.
     * Retorna empty se o token não existir (expirado, revogado ou replay detectado).
     *
     * @param tokenHash    hash do token a ser consumido
     * @param newTokenHash hash do novo token a ser armazenado
     * @param newTtl       TTL do novo token
     */
    Optional<UserId> validateAndRotate(String tokenHash, String newTokenHash, Duration newTtl);

    /**
     * Revoga um refresh token específico (logout de sessão única).
     *
     * @param tokenHash hash do token a revogar
     */
    void revoke(String tokenHash);

    /**
     * Revoga todos os refresh tokens do usuário (bloqueio de conta, replay detectado).
     *
     * @param userId identificador do usuário
     */
    void revokeAllForUser(UserId userId);
}
