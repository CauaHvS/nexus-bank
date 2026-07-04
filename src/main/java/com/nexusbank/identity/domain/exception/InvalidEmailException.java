// Exceção de domínio lançada quando um valor não satisfaz o formato de e-mail.
// Lançada pelo value object Email na construção.
// O adaptador de entrada (controller) mapeia esta exceção para HTTP 422 com ProblemDetail.
package com.nexusbank.identity.domain.exception;

public class InvalidEmailException extends RuntimeException {

    public InvalidEmailException(String value) {
        super("E-mail inválido: " + value);
    }
}
