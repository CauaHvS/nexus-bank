// Exceção de domínio lançada quando uma tentativa de registro usa um e-mail
// que já pertence a outro usuário no sistema.
// Lançada pelo caso de uso RegisterUserUseCase antes de criar o agregado.
// O adaptador de entrada mapeia para HTTP 409 com ProblemDetail.
package com.nexusbank.identity.domain.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("E-mail já cadastrado: " + email);
    }
}
