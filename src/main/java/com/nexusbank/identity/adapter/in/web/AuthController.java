package com.nexusbank.identity.adapter.in.web;

import com.nexusbank.identity.application.dto.AuthResult;
import com.nexusbank.identity.application.dto.RegisterUserCommand;
import com.nexusbank.identity.application.dto.UserView;
import com.nexusbank.identity.application.usecase.AuthenticateUserUseCase;
import com.nexusbank.identity.application.usecase.RegisterUserUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;

    public AuthController(RegisterUserUseCase registerUser,
                          AuthenticateUserUseCase authenticateUser) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
    }

    record RegisterRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String cpf,
        String phone,
        @NotBlank @Size(min = 8) String password
    ) {}

    record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest req) {
        return registerUser.execute(
            new RegisterUserCommand(req.name(), req.email(), req.cpf(), req.phone(), req.password())
        );
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody LoginRequest req) {
        return authenticateUser.execute(req.email(), req.password());
    }
}
