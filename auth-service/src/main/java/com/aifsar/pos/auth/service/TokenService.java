package com.aifsar.pos.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.aifsar.pos.auth.domain.UserAccount;
import com.aifsar.pos.common.security.JwtSecuritySupport;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Mints RS256 access tokens and opaque refresh tokens. */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder encoder;
    private final String keyId;
    private final String issuer;
    private final String audience;

    public TokenService(JwtEncoder encoder, RSAKey signingKey,
                        @Value("${security.jwt.issuer}") String issuer,
                        @Value("${security.jwt.audience}") String audience) {
        this.encoder = encoder;
        this.keyId = signingKey.getKeyID();
        this.issuer = issuer;
        this.audience = audience;
    }

    public String accessToken(UserAccount account, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(account.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(JwtSecuritySupport.CLAIM_ROLES, account.roleList());
        if (account.getMerchantId() != null) {
            claims.claim(JwtSecuritySupport.CLAIM_MERCHANT_ID, account.getMerchantId());
        }
        if (account.getTerminalId() != null) {
            claims.claim(JwtSecuritySupport.CLAIM_TERMINAL_ID, account.getTerminalId());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /** 256 bits of randomness, URL-safe. Returned to the client once; only the hash is stored. */
    public String newRefreshTokenValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
