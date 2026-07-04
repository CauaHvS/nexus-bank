// Evento de domínio emitido pelo agregado User no momento do registro.
// É um record imutável que captura o estado relevante no instante da ocorrência.
// Publicado pelo caso de uso RegisterUserUseCase após persistência bem-sucedida
// via ApplicationEventPublisher (Spring) ou Kafka, dependendo do consumidor.
// Outros módulos (ex: Notifications) consomem este evento sem acessar internals
// do módulo Identity.
package com.nexusbank.identity.domain.event;

import com.nexusbank.identity.domain.model.Email;
import com.nexusbank.identity.domain.model.UserId;

import java.time.Instant;

public record UserRegistered(
        UserId userId,
        Email email,
        String name,
        Instant occurredAt
) {}
