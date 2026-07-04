package com.nexusbank.identity.application.usecase;

import com.nexusbank.identity.application.dto.RegisterUserCommand;
import com.nexusbank.identity.application.dto.UserView;
import com.nexusbank.identity.domain.exception.EmailAlreadyRegisteredException;
import com.nexusbank.identity.domain.model.Cpf;
import com.nexusbank.identity.domain.model.Email;
import com.nexusbank.identity.domain.model.User;
import com.nexusbank.identity.domain.port.out.PasswordEncoder;
import com.nexusbank.identity.domain.port.out.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterUserUseCase(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserView execute(RegisterUserCommand command) {
        Email email = new Email(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(command.email());
        }
        String hash = passwordEncoder.encode(command.password());
        User user = User.register(command.name(), email, new Cpf(command.cpf()), command.phone(), hash);
        User saved = userRepository.save(user);
        saved.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return new UserView(saved.getId().value(), saved.getEmail().value(), saved.getName());
    }
}
