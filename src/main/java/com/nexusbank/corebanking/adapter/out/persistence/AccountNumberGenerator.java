package com.nexusbank.corebanking.adapter.out.persistence;

import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;

/** Gera número de conta único no formato NNNNNN-D (6 dígitos + dígito verificador simples). */
@Component
public class AccountNumberGenerator {
    public String generate() {
        int base = ThreadLocalRandom.current().nextInt(100000, 999999);
        int digit = base % 9 + 1;
        return base + "-" + digit;
    }
}
