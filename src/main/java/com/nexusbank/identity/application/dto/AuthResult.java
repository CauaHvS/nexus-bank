// DTO de saída dos casos de uso de autenticação (login e refresh).
// Representa o par de tokens emitidos, seu tipo e tempo de expiração do access token.
// O factory method bearer() é um atalho para o tipo padrão Bearer JWT.
// Sem dependência de framework; mapeado para o response HTTP pelo controller.
package com.nexusbank.identity.application.dto;

public record AuthResult(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {

    public static AuthResult bearer(String access, String refresh, long expiresIn) {
        return new AuthResult(access, refresh, expiresIn, "Bearer");
    }
}
