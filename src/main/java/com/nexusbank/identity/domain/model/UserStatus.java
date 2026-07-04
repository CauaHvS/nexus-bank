// Enum que representa o ciclo de vida de um usuário no sistema.
// ACTIVE: conta operacional; pode autenticar e realizar operações.
// INACTIVE: conta desativada por iniciativa do próprio usuário ou expiração.
// LOCKED: conta bloqueada por suspeita de fraude ou tentativas excessivas de login.
// Transições de status são controladas exclusivamente por métodos do agregado User,
// nunca por atribuição direta de fora do domínio.
package com.nexusbank.identity.domain.model;

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    LOCKED
}
