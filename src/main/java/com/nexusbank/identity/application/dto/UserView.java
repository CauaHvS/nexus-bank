// DTO de saída do caso de uso RegisterUserUseCase.
// Representa a visão pública do usuário recém-criado, conforme o contrato OpenAPI
// (POST /auth/register 201). Não expõe dados sensíveis (hash de senha, CPF, status).
// Sem dependência de framework.
package com.nexusbank.identity.application.dto;

import java.util.UUID;

public record UserView(
        UUID userId,
        String email,
        String name
) {}
