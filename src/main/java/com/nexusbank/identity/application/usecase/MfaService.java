package com.nexusbank.identity.application.usecase;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

/**
 * Serviço de MFA TOTP (Time-based One-Time Password).
 * Gera secret, valida código de 6 dígitos com janela de tolerância de 1 período (30s).
 */
@Service
public class MfaService {

    private final DefaultSecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final DefaultCodeVerifier codeVerifier;

    public MfaService() {
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
        this.codeVerifier.setAllowedTimePeriodDiscrepancy(1);
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public boolean verifyCode(String secret, String code) {
        try {
            return codeVerifier.isValidCode(secret, code);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gera o URI padrão otpauth:// para exibição como QR code no autenticador.
     *
     * @param secret secret TOTP base32
     * @param email  e-mail do usuário (label)
     * @param issuer nome do emissor exibido no app autenticador
     * @return URI no formato otpauth://totp/issuer:email?secret=...&issuer=...
     */
    public String generateOtpAuthUri(String secret, String email, String issuer) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                issuer, email, secret, issuer);
    }
}
