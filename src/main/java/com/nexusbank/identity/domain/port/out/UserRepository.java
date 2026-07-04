/**
 * Porta de saída: contrato que o domínio define para persistência de User.
 *
 * Posicionada em port/out porque a direção da dependência é:
 *   domínio define a interface -> adaptador de persistência (JPA) implementa.
 * O domínio nunca importa JPA; o adaptador importa o domínio.
 *
 * O adaptador concreto vive em:
 *   adapter/out/persistence/JpaUserRepository (implementa esta interface)
 *
 * Sem anotações de framework nesta interface.
 */
package com.nexusbank.identity.domain.port.out;

import com.nexusbank.identity.domain.model.Email;
import com.nexusbank.identity.domain.model.User;
import com.nexusbank.identity.domain.model.UserId;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
