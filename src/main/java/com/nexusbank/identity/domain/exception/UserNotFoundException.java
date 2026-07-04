// Exceção de domínio lançada quando um usuário não é encontrado pelo identificador
// fornecido (userId ou e-mail).
// Lançada pelos casos de uso ao consultar o UserRepository sem resultado.
// O adaptador de entrada mapeia para HTTP 404 com ProblemDetail.
package com.nexusbank.identity.domain.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String identifier) {
        super("Usuário não encontrado: " + identifier);
    }
}
