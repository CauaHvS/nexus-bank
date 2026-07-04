package com.nexusbank.identity.domain.model;

import com.nexusbank.identity.domain.event.UserRegistered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User")
class UserTest {

    private Email   email;
    private Cpf     cpf;
    private String  name;
    private String  phone;
    private String  passwordHash;

    @BeforeEach
    void setUp() {
        email        = new Email("joao@example.com");
        cpf          = new Cpf("529.982.247-25");
        name         = "Joao Silva";
        phone        = "+5511999999999";
        passwordHash = "$2a$10$hashedpasswordvalue";
    }

    @Test
    @DisplayName("register() deve criar usuário com status ACTIVE e role CUSTOMER")
    void register_withValidData_createsActiveUserWithCustomerRole() {
        User user = User.register(name, email, cpf, phone, passwordHash);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("register() deve criar usuário com os dados informados")
    void register_withValidData_storesAllProvidedFields() {
        User user = User.register(name, email, cpf, phone, passwordHash);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getCpf()).isEqualTo(cpf);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getPhone()).isEqualTo(phone);
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("register() deve emitir exatamente 1 evento UserRegistered com dados corretos")
    void register_withValidData_emitsExactlyOneUserRegisteredEvent() {
        User user = User.register(name, email, cpf, phone, passwordHash);

        List<Object> events = user.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(UserRegistered.class);

        UserRegistered event = (UserRegistered) events.get(0);
        assertThat(event.userId()).isEqualTo(user.getId());
        assertThat(event.email()).isEqualTo(email);
        assertThat(event.name()).isEqualTo(name);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("pullDomainEvents() deve esvaziar a lista; segunda chamada retorna vazio")
    void pullDomainEvents_calledTwice_secondCallReturnsEmptyList() {
        User user = User.register(name, email, cpf, phone, passwordHash);

        List<Object> firstPull  = user.pullDomainEvents();
        List<Object> secondPull = user.pullDomainEvents();

        assertThat(firstPull).hasSize(1);
        assertThat(secondPull).isEmpty();
    }

    @Test
    @DisplayName("lock() deve mudar o status para LOCKED")
    void lock_onActiveUser_changesStatusToLocked() {
        User user = User.register(name, email, cpf, phone, passwordHash);

        user.lock();

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("lock() chamado duas vezes deve ser idempotente e não lançar exceção")
    void lock_calledTwice_isIdempotent() {
        User user = User.register(name, email, cpf, phone, passwordHash);

        user.lock();
        user.lock(); // não deve lançar

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    @DisplayName("activate() deve mudar o status de LOCKED para ACTIVE")
    void activate_onLockedUser_changesStatusToActive() {
        User user = User.register(name, email, cpf, phone, passwordHash);
        user.lock();

        user.activate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive() deve retornar true para ACTIVE e false para LOCKED")
    void isActive_reflectsCurrentStatus() {
        User user = User.register(name, email, cpf, phone, passwordHash);

        assertThat(user.isActive()).isTrue();

        user.lock();
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("passwordHash armazenado deve ser o valor passado, não a senha original")
    void register_storesPasswordHashNotPlaintext() {
        String plainPassword = "minha-senha-secreta";
        // Simula o que a camada de aplicação faz: passa o hash, nunca o plaintext.
        // O domínio deve armazenar exatamente o que recebeu.
        String hash = "$2a$10$fakehashfor" + plainPassword;

        User user = User.register(name, email, cpf, phone, hash);

        assertThat(user.getPasswordHash()).isEqualTo(hash);
        assertThat(user.getPasswordHash()).isNotEqualTo(plainPassword);
    }
}
