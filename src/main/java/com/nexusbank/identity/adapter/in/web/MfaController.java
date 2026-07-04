package com.nexusbank.identity.adapter.in.web;

import com.nexusbank.identity.application.usecase.MfaService;
import com.nexusbank.identity.domain.model.UserId;
import com.nexusbank.identity.domain.port.out.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final UserRepository userRepository;

    public MfaController(MfaService mfaService, UserRepository userRepository) {
        this.mfaService = mfaService;
        this.userRepository = userRepository;
    }

    record VerifyRequest(@NotBlank String code) {}

    /**
     * Gera um novo secret TOTP e retorna o otpauth:// URI para exibição como QR code.
     * O secret é persistido imediatamente; o MFA só tem efeito operacional após
     * confirmação via /auth/mfa/verify.
     */
    @PostMapping("/setup")
    public ResponseEntity<Map<String, String>> setup(@AuthenticationPrincipal String userId) {
        var user = userRepository.findById(UserId.of(userId)).orElseThrow();
        String secret = mfaService.generateSecret();
        String uri = mfaService.generateOtpAuthUri(secret, user.getEmail().value(), "NexusBank");
        user.enableMfa(secret);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("otpAuthUri", uri, "secret", secret));
    }

    /**
     * Confirma que o secret foi configurado corretamente no autenticador.
     * O cliente deve enviar o código TOTP atual após escanear o QR code.
     */
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal String userId,
                                       @Valid @RequestBody VerifyRequest req) {
        var user = userRepository.findById(UserId.of(userId)).orElseThrow();
        if (!user.isMfaEnabled() || !mfaService.verifyCode(user.getMfaSecret(), req.code())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }
}
