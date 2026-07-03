package com.nexusbank.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração mínima de segurança para fase de infra.
 *
 * Libera /actuator/health e /actuator/info sem autenticação para que
 * healthchecks do compose e futuros probes de liveness/readiness funcionem.
 * Todos os demais endpoints exigem autenticação.
 *
 * Esta configuração será expandida na fatia de Identity com JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
