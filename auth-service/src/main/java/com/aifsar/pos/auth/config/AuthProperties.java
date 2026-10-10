package com.aifsar.pos.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Tokens tokens, Lockout lockout, Seed seed) {

    /**
     * @param privateKeyPem PKCS#8 RSA private key. Leave blank locally to generate a key at
     *                      startup; in the cloud it MUST come from a secret so every replica
     *                      signs with the same key (otherwise tokens fail on other pods).
     */
    public record Tokens(Duration accessTokenTtl, Duration terminalTokenTtl, Duration refreshTokenTtl,
                         String privateKeyPem) {
    }

    public record Lockout(int maxFailedAttempts, Duration lockDuration) {
    }

    public record Seed(boolean enabled) {
    }
}
