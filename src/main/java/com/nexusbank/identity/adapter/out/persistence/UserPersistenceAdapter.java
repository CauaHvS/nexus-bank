package com.nexusbank.identity.adapter.out.persistence;

import com.nexusbank.identity.domain.model.*;
import com.nexusbank.identity.domain.port.out.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    UserPersistenceAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        UserJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }

    private UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.getId().value(),
                user.getEmail().value(),
                user.getCpf().value(),
                user.getName(),
                user.getPhone(),
                user.getPasswordHash(),
                user.getStatus(),
                user.getRole(),
                user.getCreatedAt(),
                user.getMfaSecret(),
                user.isMfaEnabled()
        );
    }

    private User toDomain(UserJpaEntity e) {
        return User.reconstitute(
                UserId.of(e.id.toString()),
                new Email(e.email),
                new Cpf(e.cpf),
                e.name,
                e.phone,
                e.passwordHash,
                e.status,
                e.role,
                e.createdAt,
                e.mfaSecret,
                e.mfaEnabled
        );
    }
}
