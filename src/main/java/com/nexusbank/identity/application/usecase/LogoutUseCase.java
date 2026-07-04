package com.nexusbank.identity.application.usecase;

import com.nexusbank.identity.domain.port.out.TokenStore;
import org.springframework.stereotype.Service;

@Service
public class LogoutUseCase {

    private final TokenStore tokenStore;

    public LogoutUseCase(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    public void execute(String refreshToken) {
        tokenStore.revoke(TokenHasher.hash(refreshToken));
    }
}
