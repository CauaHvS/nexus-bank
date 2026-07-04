package com.nexusbank.identity.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "identity")
class UserJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @Column(name = "email", nullable = false, unique = true)
    String email;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    String cpf;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "phone")
    String phone;

    @Column(name = "password_hash", nullable = false)
    String passwordHash;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    com.nexusbank.identity.domain.model.UserStatus status;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    com.nexusbank.identity.domain.model.Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "mfa_secret", length = 64)
    String mfaSecret;

    @Column(name = "mfa_enabled", nullable = false)
    boolean mfaEnabled = false;

    protected UserJpaEntity() {}

    UserJpaEntity(UUID id, String email, String cpf, String name, String phone,
                  String passwordHash,
                  com.nexusbank.identity.domain.model.UserStatus status,
                  com.nexusbank.identity.domain.model.Role role,
                  Instant createdAt,
                  String mfaSecret,
                  boolean mfaEnabled) {
        this.id = id;
        this.email = email;
        this.cpf = cpf;
        this.name = name;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
        this.mfaSecret = mfaSecret;
        this.mfaEnabled = mfaEnabled;
    }
}
