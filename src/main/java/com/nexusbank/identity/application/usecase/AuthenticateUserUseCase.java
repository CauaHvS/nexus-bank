package com.nexusbank.identity.application.usecase;

import com.nexusbank.identity.application.dto.AuthResult;
import com.nexusbank.identity.domain.exception.InvalidCredentialsException;
import com.nexusbank.identity.domain.model.Email;
import com.nexusbank.identity.domain.port.out.PasswordEncoder;
import com.nexusbank.identity.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticateUserUseCase(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResult execute(String emailStr, String rawPassword) {
        var email = new Email(emailStr);
        var user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) throw new InvalidCredentialsException();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash()))
            throw new InvalidCredentialsException();
        return jwtService.generateTokenPair(user.getId().value().toString(), user.getRole().name());
    }
}
