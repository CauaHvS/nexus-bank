// Command (objeto de entrada) para o caso de uso RegisterUserUseCase.
// Transporta os dados brutos vindos do adaptador de entrada (controller HTTP).
// A validação de formato (Email, Cpf) ocorre dentro do caso de uso ao construir
// os value objects do domínio — não aqui, para manter a lógica no domínio.
// É um record imutável; não carrega anotações de framework.
package com.nexusbank.identity.application.dto;

public record RegisterUserCommand(
        String name,
        String email,
        String cpf,
        String phone,
        String password
) {}
