package com.nexusbank.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Nexus Bank API",
                version = "1.0.0",
                description = """
                        API do Nexus Bank — banco digital distribuído implementado como monólito modular \
                        (Spring Modulith). Expõe os bounded contexts de Identity, Core Banking, Payments \
                        (com Saga + Transactional Outbox) e Fraud Detection.

                        **Autenticação:** JWT Bearer — faça login em `POST /auth/login`, copie o \
                        `accessToken` retornado e clique em **Authorize** para autenticar as demais chamadas.
                        """,
                contact = @Contact(name = "Nexus Bank"),
                license = @License(name = "MIT")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Desenvolvimento local"),
                @Server(url = "http://localhost:80",   description = "Stack Docker (via Nginx)")
        },
        tags = {
                @Tag(name = "Autenticação",    description = "Registro, login, refresh e logout"),
                @Tag(name = "MFA",             description = "Autenticação multifator (TOTP)"),
                @Tag(name = "Contas",          description = "Abertura de conta, saldo, extrato e depósito"),
                @Tag(name = "Transferências",  description = "Saga de transferência: PIX, TED e interna com idempotência"),
                @Tag(name = "Fraude",          description = "Revisão manual de transferências marcadas como suspeitas")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token JWT obtido em POST /auth/login. Copie o accessToken e cole acima."
)
public class OpenApiConfig {
}
