// Exceção de domínio lançada quando e-mail ou senha não correspondem a um usuário
// ativo no sistema.
// Mensagem propositalmente genérica para não revelar se o e-mail existe ou não
// (mitigação de user enumeration).
// Lançada pelo caso de uso AuthenticateUserUseCase.
// O adaptador de entrada mapeia para HTTP 401 com ProblemDetail.
package com.nexusbank.identity.domain.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciais inválidas.");
    }
}
