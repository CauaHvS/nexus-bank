/**
 * Agregado raiz do contexto Identity.
 *
 * Responsabilidades:
 * - Guardar as invariantes do usuário: e-mail único (verificado na camada de
 *   aplicação antes de chamar register), senha nunca em texto plano (apenas hash
 *   BCrypt é armazenado), status controlado por métodos de domínio.
 * - Emitir eventos de domínio (UserRegistered) que serão publicados pelo caso de
 *   uso após persistência bem-sucedida.
 * - Expor mutações somente por métodos com semântica de negócio (lock, activate),
 *   nunca por setters públicos.
 *
 * O que este agregado NÃO faz:
 * - Não persiste a si mesmo (responsabilidade do adaptador de persistência).
 * - Não gera nem valida tokens JWT (responsabilidade do caso de uso + adaptador).
 * - Não conhece Spring, JPA ou qualquer framework.
 */
package com.nexusbank.identity.domain.model;

import com.nexusbank.identity.domain.event.UserRegistered;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class User {

    private final UserId id;
    private final Email email;
    private final Cpf cpf;
    private String name;
    private String phone;
    private String passwordHash;
    private UserStatus status;
    private final Role role;
    private final Instant createdAt;
    private String mfaSecret;
    private boolean mfaEnabled;
    private final List<Object> domainEvents = new ArrayList<>();

    private User(UserId id, Email email, Cpf cpf, String name, String phone,
                 String passwordHash, Role role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.cpf = cpf;
        this.name = name;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
        this.role = role;
        this.createdAt = createdAt;
        this.mfaSecret = null;
        this.mfaEnabled = false;
    }

    private User(UserId id, Email email, Cpf cpf, String name, String phone,
                 String passwordHash, UserStatus status, Role role, Instant createdAt,
                 String mfaSecret, boolean mfaEnabled) {
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

    /**
     * Factory method para o caso de uso de registro.
     * Emite o evento UserRegistered para ser publicado após persistência.
     *
     * @param name         nome completo
     * @param email        e-mail validado
     * @param cpf          CPF validado e normalizado
     * @param phone        telefone
     * @param passwordHash hash BCrypt da senha (nunca a senha em texto plano)
     */
    public static User register(String name, Email email, Cpf cpf, String phone,
                                String passwordHash) {
        User user = new User(
                UserId.generate(), email, cpf, name, phone,
                passwordHash, Role.CUSTOMER, Instant.now()
        );
        user.domainEvents.add(new UserRegistered(user.id, user.email, user.name, user.createdAt));
        return user;
    }

    /**
     * Factory para reconstituição a partir de persistência.
     * Não emite eventos de domínio — o estado já existia antes.
     */
    public static User reconstitute(UserId id, Email email, Cpf cpf, String name, String phone,
                                    String passwordHash, UserStatus status, Role role, Instant createdAt,
                                    String mfaSecret, boolean mfaEnabled) {
        return new User(id, email, cpf, name, phone, passwordHash, status, role, createdAt,
                mfaSecret, mfaEnabled);
    }

    /**
     * Bloqueia a conta. Idempotente: se já estiver bloqueada, não faz nada.
     * Usado por Fraud ao detectar risco elevado.
     */
    public void lock() {
        if (this.status == UserStatus.LOCKED) return;
        this.status = UserStatus.LOCKED;
    }

    /**
     * Reativa a conta para o status ACTIVE.
     * Usado em fluxo de desbloqueio administrativo.
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public void enableMfa(String secret) {
        this.mfaSecret = secret;
        this.mfaEnabled = true;
    }

    public void disableMfa() {
        this.mfaSecret = null;
        this.mfaEnabled = false;
    }

    /**
     * Retorna e limpa os eventos de domínio pendentes.
     * O caso de uso chama este método após persistência para publicar os eventos.
     */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    // Getters — sem setters públicos; mutação apenas por métodos de domínio acima

    public UserId getId() { return id; }
    public Email getEmail() { return email; }
    public Cpf getCpf() { return cpf; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public UserStatus getStatus() { return status; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMfaSecret() { return mfaSecret; }
    public boolean isMfaEnabled() { return mfaEnabled; }
}
