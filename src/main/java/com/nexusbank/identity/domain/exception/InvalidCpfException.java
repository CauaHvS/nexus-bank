// Exceção de domínio lançada quando um valor não passa na validação do algoritmo de CPF.
// Lançada pelo value object Cpf na construção.
// O adaptador de entrada (controller) mapeia esta exceção para HTTP 422 com ProblemDetail.
package com.nexusbank.identity.domain.exception;

public class InvalidCpfException extends RuntimeException {

    public InvalidCpfException(String value) {
        super("CPF inválido: " + value);
    }
}
