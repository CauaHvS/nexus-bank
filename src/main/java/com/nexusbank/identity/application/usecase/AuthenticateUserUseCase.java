package com.nexusbank.identity.application.usecase;

import com.nexusbank.identity.application.dto.AuthResult;
import com.nexusbank.identity.domain.exception.InvalidCredentialsException;
import com.nexusbank.identity.domain.model.Email;
import com.nexusbank.identity.domain.port.out.PasswordEncoder;
import com.nexusbank.identity.domain.port.out.TokenStore;
import com.nexusbank.identity.domain.port.out.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final long refreshTtlSeconds;

    public AuthenticateUserUseCase(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   JwtService jwtService,
                                   TokenStore tokenStore,
                                   @Value("${security.jwt.refresh-token-ttl-seconds:604800}") long refreshTtl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.refreshTtlSeconds = refreshTtl;
    }

    @Transactional(readOnly = true)
    public AuthResult execute(String emailStr, String rawPassword) {
        var email = new Email(emailStr);
        var user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) throw new InvalidCredentialsException();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash()))
            throw new InvalidCredentialsException();

        AuthResult tokens = jwtService.generateTokenPair(
                user.getId().value().toString(), user.getRole().name());

        tokenStore.storeRefreshToken(
                user.getId(),
                TokenHasher.hash(tokens.refreshToken()),
                Duration.ofSeconds(refreshTtlSeconds));

        return tokens;
    }
}
