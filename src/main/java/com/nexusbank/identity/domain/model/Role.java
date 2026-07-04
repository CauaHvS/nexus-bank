// Enum de papéis de usuário no sistema.
// CUSTOMER: cliente padrão do banco.
// ADMIN: operador com acesso privilegiado (backoffice).
// Novos papéis (ex: SUPPORT, COMPLIANCE) podem ser adicionados aqui sem
// alterar o agregado User.
package com.nexusbank.identity.domain.model;

public enum Role {
    CUSTOMER,
    ADMIN
}
